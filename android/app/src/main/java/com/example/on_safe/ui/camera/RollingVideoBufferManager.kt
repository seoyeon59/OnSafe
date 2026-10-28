package com.example.on_safe.ui.camera

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * 카메라 바인딩은 [CameraModeActivity] 소유(`videoCapture` 제공만 하고 바인딩은 안 함).
 * 이 클래스는 세그먼트 연속 녹화 + 링버퍼 관리 + 위험 이벤트 시 pre/post 스플라이스만 담당.
 */
class RollingVideoBufferManager(private val context: Context) {

    companion object {
        private const val TAG = "RollingVideoBuffer"
        private const val SEGMENT_DURATION_MS = 15_000L
        private const val RING_BUFFER_CAPACITY = 12   // 3분
        private const val PRE_EVENT_SEGMENTS = 8       // 약 2분
        private const val POST_EVENT_SEGMENTS = 8      // 약 2분
        private const val TARGET_BITRATE = 1_500_000
        // 할 일이 없으면 이 시간 뒤 스레드가 스스로 끝난다 — 직접 shutdown하지 않는다(아래 executor 참고)
        private const val EXECUTOR_IDLE_TIMEOUT_SEC = 30L
    }

    /**
     * 녹화 이벤트 수신·클립 합성 전용 단일 스레드.
     *
     * 예전에는 Recorder 내부(인코더)에도 이 실행기를 넘기고 stop() 1초 뒤 shutdown했다. 기기에 따라
     * 정지 후 인코더 출력이 1초를 넘겨 이어지면 종료된 실행기에 작업이 들어가 RejectedExecutionException으로
     * 앱이 죽었다(촬영 정지 시 크래시). 그래서
     * - Recorder 내부는 CameraX 기본 실행기를 쓰게 두고,
     * - 이 실행기는 shutdown하지 않는다 — 대신 유휴 시 스레드가 스스로 끝나게 해 인스턴스마다 스레드가 쌓이지 않는다.
     * 늦게 도착한 Finalize·합성 작업도 거절되지 않는다.
     */
    private val executor = ThreadPoolExecutor(
        1, 1, EXECUTOR_IDLE_TIMEOUT_SEC, TimeUnit.SECONDS, LinkedBlockingQueue()
    ).apply { allowCoreThreadTimeOut(true) }

    val videoCapture: VideoCapture<Recorder> by lazy {
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .setTargetVideoEncodingBitRate(TARGET_BITRATE)
            .build()
        VideoCapture.withOutput(recorder)
    }

    private val bufferDir: File by lazy {
        File(context.cacheDir, "fall_buffer").apply { mkdirs() }
    }

    private val segments = ArrayDeque<File>()
    private var currentRecording: Recording? = null
    private var segmentIndex = 0

    // 메인(start·stop)과 녹화 이벤트 스레드(Finalize)가 함께 읽는다
    @Volatile
    private var running = false

    // 위험 이벤트 스플라이스 진행 상태 — 한 번에 하나만, synchronized(this)로 보호
    private var capturingLogId: String? = null
    private val postSegments = mutableListOf<File>()
    private var preSegmentsSnapshot: List<File> = emptyList()
    private var onClipReady: ((File) -> Unit)? = null
    private var onClipError: ((Throwable) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val rotateRunnable = Runnable { rotateSegment() }

    fun start() {
        running = true
        rotateSegment()
    }

    fun stop() {
        running = false
        mainHandler.removeCallbacks(rotateRunnable)
        currentRecording?.stop()
        currentRecording = null
        synchronized(this) {
            segments.forEach { it.delete() }
            segments.clear()
            capturingLogId = null
            postSegments.clear()
            preSegmentsSnapshot = emptyList()
            onClipReady = null
            onClipError = null
        }
        // executor는 shutdown하지 않는다 — 마지막 세그먼트의 Finalize가 이 뒤에 도착할 수 있다.
        // 유휴가 되면 스레드가 스스로 끝난다(EXECUTOR_IDLE_TIMEOUT_SEC)
    }

    // RECORD_AUDIO는 호출부(CameraModeActivity)가 카메라 권한과 함께 확인 후 시작
    @SuppressLint("MissingPermission")
    private fun rotateSegment() {
        if (!running) return
        currentRecording?.stop()

        val file = File(bufferDir, "seg_${segmentIndex++}_${System.currentTimeMillis()}.mp4")
        val outputOptions = FileOutputOptions.Builder(file).build()
        currentRecording = videoCapture.output
            .prepareRecording(context, outputOptions)
            .withAudioEnabled()
            .start(executor) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    if (event.hasError()) {
                        Log.w(TAG, "세그먼트 녹화 오류: errorCode=${event.error}", event.cause)
                        file.delete()
                    } else {
                        onSegmentFinalized(file)
                    }
                }
            }
        mainHandler.postDelayed(rotateRunnable, SEGMENT_DURATION_MS)
    }

    private fun onSegmentFinalized(file: File) {
        // stop() 뒤에 마무리된 마지막 세그먼트 — 버퍼는 이미 비웠으니 캐시에 남기지 않는다
        if (!running) {
            file.delete()
            return
        }
        synchronized(this) {
            segments.addLast(file)
            while (segments.size > RING_BUFFER_CAPACITY) {
                segments.pollFirst()?.delete()
            }

            if (capturingLogId != null) {
                postSegments.add(file)
                if (postSegments.size >= POST_EVENT_SEGMENTS) {
                    finishCaptureLocked()
                }
            }
        }
    }

    /** 위험 이벤트 시 호출 — 진행 중인 스플라이스가 있으면 무시 */
    fun captureDangerClip(logId: String, onReady: (File) -> Unit, onError: (Throwable) -> Unit) {
        synchronized(this) {
            if (capturingLogId != null) {
                Log.w(TAG, "이미 스플라이스 진행 중이라 새 위험 이벤트($logId) 무시")
                return
            }
            preSegmentsSnapshot = segments.toList().takeLast(PRE_EVENT_SEGMENTS)
            postSegments.clear()
            onClipReady = onReady
            onClipError = onError
            capturingLogId = logId
        }
    }

    // 호출부(onSegmentFinalized)가 이미 synchronized(this) 안이라 별도 락 불필요
    private fun finishCaptureLocked() {
        val logId = capturingLogId ?: return
        val pre = preSegmentsSnapshot
        val post = postSegments.toList()
        val readyCb = onClipReady
        val errorCb = onClipError

        capturingLogId = null
        preSegmentsSnapshot = emptyList()
        postSegments.clear()
        onClipReady = null
        onClipError = null

        try {
            executor.execute {
                val outputFile = File(bufferDir, "clip_$logId.mp4")
                try {
                    FallClipComposer.compose(pre + post, outputFile)
                    readyCb?.invoke(outputFile)
                } catch (e: Exception) {
                    Log.w(TAG, "클립 합성 실패 (logId=$logId)", e)
                    errorCb?.invoke(e)
                }
            }
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            // stop()으로 executor가 종료된 뒤 위험 이벤트가 뒤늦게 마무리된 경우 —
            // 크래시 대신 로그만 남기고 실패 처리
            Log.w(TAG, "executor 종료 이후라 클립 합성 제출 실패 (logId=$logId)", e)
            errorCb?.invoke(e)
        }
    }
}
