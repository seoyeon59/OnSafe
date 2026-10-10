package com.example.on_safe.ui.camera

import android.content.Context
import android.util.Log
import com.example.on_safe.BuildConfig
import com.example.on_safe.data.repository.LivePublishCredentials
import io.livekit.android.AudioOptions
import io.livekit.android.ConnectOptions
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.audio.NoAudioHandler
import io.livekit.android.events.DisconnectReason
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 피보호자(카메라 모드) 실시간 영상 송출 — LiveKit 방 접속 담당.
 *
 * 송출 전용 토큰으로 방(`live-{내 userId}`)에 접속한다. 영상 트랙 송출은 접속된 [room]에 별도로 붙인다.
 * - 카메라는 CameraX가 포즈 분석·녹화에 쓰고 있어 SDK가 직접 열면 안 된다 — 접속 시 audio/video 자동 송출은 끈 채로 둔다.
 * - 피보호자는 보기만 하지 않으므로 다른 참가자 트랙은 구독하지 않는다(autoSubscribe=false).
 * - 자체 타이머로 멈추지 않는다 — 서버가 방을 지울 때(만료·보호자 종료·해제·동의 철회)의 연결 끊김으로 끝낸다.
 *   보호자 연장은 피보호자에게 알리지 않으므로 expires_at은 참고용이다.
 *
 * 화면 회전에도 송출이 끊기지 않도록 ViewModel이 소유한다 — 그래서 Application Context만 쓴다.
 */
class LiveRoomPublisher(
    context: Context,
    private val scope: CoroutineScope,
    private val listener: Listener
) {
    interface Listener {
        /** 방 접속 완료 — 이후 영상 트랙을 송출할 수 있다 */
        fun onConnected(room: Room)

        /** 접속 실패 또는 접속 후 끊김(서버의 방 삭제 포함). 우리가 [disconnect]한 경우엔 부르지 않는다 */
        fun onEnded(reason: String)
    }

    private val appContext = context.applicationContext

    private var room: Room? = null
    private var job: Job? = null

    // 우리가 끊는 중인지 — 직접 끊은 Disconnected 이벤트를 서버 종료로 오인하지 않게
    private var closingByClient = false

    val connectedRoom: Room? get() = room?.takeIf { it.state == Room.State.CONNECTED }

    val isActive: Boolean get() = job?.isActive == true

    fun connect(credentials: LivePublishCredentials) {
        if (isActive) return
        closingByClient = false
        // 네이티브 라이브러리 로드 실패 등이 메인 스레드에서 새면 앱(녹화·분석)이 죽는다
        val newRoom = try {
            LiveKit.create(appContext, overrides = NO_AUDIO_OVERRIDES)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "LiveKit 방 생성 실패", e)
            listener.onEnded("create_failed: ${e.javaClass.simpleName}")
            return
        }
        room = newRoom
        job = scope.launch {
            // SharedFlow라 접속 전에 구독해야 접속 직후 이벤트를 놓치지 않는다
            launch(start = CoroutineStart.UNDISPATCHED) {
                newRoom.events.collect { event -> handleEvent(event) }
            }
            try {
                newRoom.connect(
                    url = credentials.serverUrl,
                    token = credentials.token,
                    options = ConnectOptions(autoSubscribe = false)
                )
                if (BuildConfig.DEBUG) Log.d(TAG, "LiveKit 방 접속 — room=${credentials.room}")
                listener.onConnected(newRoom)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "LiveKit 방 접속 실패", e)
                end("connect_failed: ${e.javaClass.simpleName}")
            }
        }
    }

    /** 우리 쪽 종료(카메라 모드 종료 등) — [Listener.onEnded]는 부르지 않는다 */
    fun disconnect() {
        closingByClient = true
        release()
    }

    private fun handleEvent(event: RoomEvent) {
        if (event !is RoomEvent.Disconnected || closingByClient) return
        // ROOM_DELETED: 서버가 세션 만료·보호자 종료·해제·동의 철회로 방을 지움. 그 외는 네트워크 단절 등
        val reason = event.reason ?: DisconnectReason.UNKNOWN_REASON
        if (BuildConfig.DEBUG) Log.d(TAG, "LiveKit 연결 끊김 — reason=$reason")
        end(reason.name)
    }

    private fun end(reason: String) {
        if (closingByClient) return
        closingByClient = true
        // 영상 트랙 정리(리스너)를 방 해제보다 먼저 — 해제된 방에서 송출을 내리지 않게
        listener.onEnded(reason)
        release()
    }

    private fun release() {
        val current = job
        job = null
        room?.let {
            runCatching { it.disconnect() }
            runCatching { it.release() }
        }
        room = null
        current?.cancel()
    }

    private companion object {
        const val TAG = "LivePublish"

        // 낙상 녹화(롤링 버퍼)가 마이크를 쓰고 있다. LiveKit 기본 오디오 처리는 접속 시 통화 모드 전환·
        // 오디오 포커스 요청·마이크 예열을 해 녹화 오디오와 부딪힐 수 있어 전부 끈다(영상만 송출)
        val NO_AUDIO_OVERRIDES = LiveKitOverrides(
            audioOptions = AudioOptions(
                audioHandler = NoAudioHandler(),
                disableCommunicationModeWorkaround = true,
                disableAudioPrewarming = true
            )
        )
    }
}
