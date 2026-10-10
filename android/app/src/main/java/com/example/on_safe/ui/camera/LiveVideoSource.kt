package com.example.on_safe.ui.camera

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.example.on_safe.BuildConfig
import io.livekit.android.room.Room
import io.livekit.android.room.participant.VideoTrackPublishOptions
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.VideoEncoding
import io.livekit.android.room.track.video.BitmapFrameCapturer

/**
 * 카메라 모드 분석 프레임을 LiveKit 영상 트랙으로 송출하는 커스텀 영상 소스.
 *
 * 카메라는 CameraX가 포즈 분석·녹화에 이미 쓰고 있어 SDK가 직접 열 수 없다. 그래서 분석용으로 만든
 * (회전까지 맞춘) Bitmap을 [pushFrame]으로 받아 [BitmapFrameCapturer]에 넘긴다.
 * - 프레임은 분석 스레드에서 들어오고, 송출 시작·종료는 메인 스레드에서 일어나 [lock]으로 묶는다.
 * - pushBitmap은 Bitmap을 비동기로 그린다 — 넘긴 Bitmap을 재사용·recycle하면 안 된다(분석은 프레임마다 새로 만든다).
 * - 분석 프레임은 촬영(STREAMING) 중에만 나온다. 촬영 전이면 트랙은 송출되지만 화면이 오지 않는다.
 */
class LiveVideoSource {

    private val lock = Any()
    private var capturer: BitmapFrameCapturer? = null
    private var track: LocalVideoTrack? = null
    private var room: Room? = null

    val isPublishing: Boolean get() = synchronized(lock) { track != null }

    /** 접속된 방에 영상 트랙을 만들어 송출한다. 실패하면 false */
    suspend fun publish(room: Room): Boolean {
        val newCapturer = BitmapFrameCapturer()
        val newTrack = try {
            room.localParticipant.createVideoTrack(name = TRACK_NAME, capturer = newCapturer)
                .also { it.startCapture() }
        } catch (e: Exception) {
            // 여기서 예외가 새면 ViewModel 코루틴이 앱을 죽여 녹화·분석까지 멈춘다
            if (BuildConfig.DEBUG) Log.w(TAG, "영상 트랙 생성 실패", e)
            return false
        }
        // 프레임은 송출 등록 전부터 받아 둔다 — 첫 화면이 늦게 뜨지 않게
        synchronized(lock) {
            capturer = newCapturer
            track = newTrack
            this.room = room
        }
        val published = try {
            room.localParticipant.publishVideoTrack(newTrack, PUBLISH_OPTIONS)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "영상 트랙 송출 실패", e)
            false
        }
        if (!published) stop()
        else if (BuildConfig.DEBUG) Log.d(TAG, "영상 트랙 송출 시작")
        return published
    }

    // 마지막으로 넘긴 프레임 시각 — 송출 프레임 수 상한(분석 스레드에서만 접근)
    private var lastFrameAtMs = 0L

    /**
     * 분석 스레드에서 호출 — 송출 중이 아니면 무시.
     * 분석·추론을 붙잡지 않도록 빨리 돌아온다: pushBitmap은 그리기를 예약만 하고, 프레임 수는 [MAX_FPS]로 거른다
     * (그리기 예약이 쌓여 메모리·CPU를 잡아먹지 않게).
     */
    fun pushFrame(bitmap: Bitmap) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastFrameAtMs < MIN_FRAME_INTERVAL_MS) return
        synchronized(lock) {
            val current = capturer ?: return
            lastFrameAtMs = now
            try {
                // 분석 Bitmap은 이미 화면 방향으로 돌려져 있어 회전 메타데이터는 0
                current.pushBitmap(bitmap, 0)
            } catch (_: Exception) {
                // 캡처 시작 전·폐기 후 등 — 프레임 하나 버리는 것으로 충분
            }
        }
    }

    /** 송출 중단 — 방이 이미 끊겼어도 안전하게 정리 */
    fun stop() {
        val (oldTrack, oldRoom) = synchronized(lock) {
            val pair = track to room
            capturer = null
            track = null
            room = null
            pair
        }
        oldTrack ?: return
        // 각 단계 실패가 다음 정리를 막지 않고, 메인 스레드 예외로 앱(녹화·분석)을 죽이지 않게 개별 보호
        runCatching { oldRoom?.localParticipant?.unpublishTrack(oldTrack) }   // 방 삭제로 이미 끊긴 경우 등
        runCatching { oldTrack.stopCapture() }
        runCatching { oldTrack.dispose() }
    }

    private companion object {
        const val TAG = "LivePublish"
        const val TRACK_NAME = "camera"

        // 분석 프레임(약 640x480) 기준. 포즈 추론·녹화(HD) 인코딩과 CPU를 나눠 쓰므로 낮게 잡는다
        const val MAX_FPS = 15
        const val MAX_BITRATE_BPS = 600_000
        const val MIN_FRAME_INTERVAL_MS = 1000L / MAX_FPS

        // simulcast는 해상도별로 여러 번 인코딩한다 — 시청자는 보호자 한 명뿐이라 끈다
        val PUBLISH_OPTIONS = VideoTrackPublishOptions(
            videoEncoding = VideoEncoding(maxBitrate = MAX_BITRATE_BPS, maxFps = MAX_FPS),
            simulcast = false
        )
    }
}
