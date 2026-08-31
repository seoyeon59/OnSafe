package com.example.on_safe.ui.camera

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
import java.util.concurrent.Executors

/**
 * 카메라 바인딩은 [CameraModeActivity]가 소유하므로(`videoCapture`를 넘겨줄 뿐 바인딩은 안 함),
 * 이 클래스는 15초 세그먼트 연속 녹화 + 링버퍼 관리 + 위험 이벤트 시 pre/post 세그먼트 스플라이스만 담당한다.
 */
class RollingVideoBufferManager(private val context: Context) {

    companion object {
        private const val TAG = "RollingVideoBuffer"
        private const val SEGMENT_DURATION_MS = 15_000L
        private const val RING_BUFFER_CAPACITY = 12   // 3분
        private const val PRE_EVENT_SEGMENTS = 8       // 약 2분
        private const val POST_EVENT_SEGMENTS = 8      // 약 2분
        private const val TARGET_BITRATE = 1_500_000
        // stop() 직후 남은 Finalize 이벤트 전달이 끝날 시간을 벌기 위한 유예 (즉시 shutdown 시 예외)
        private const val SHUTDOWN_GRACE_MS = 1_000L
    }

    private val executor = Executors.newSingleThreadExecutor()

    val videoCapture: VideoCapture<Recorder> by lazy {
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .setExecutor(executor)
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
    private var running = false

    // 위험 이벤트 스플라이스 진행 상태 (한 번에 하나만 — synchronized(this)로 보호)
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
        // 인스턴스마다 executor가 생기므로 반드시 정리하되, 남은 Finalize 전달을 위해 유예를 둔다
        mainHandler.postDelayed({ executor.shutdown() }, SHUTDOWN_GRACE_MS)
    }

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

    /** 위험 이벤트 발생 시 호출 — 진행 중인 스플라이스가 있으면 무시(로그만 남김). */
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

    // 호출부(onSegmentFinalized)에서 이미 synchronized(this) 블록 안이므로 별도 락 안 잡음
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
            // stop()으로 executor가 이미 종료된 직후 위험 이벤트가 뒤늦게 마무리된 경우 —
            // 합성을 포기하고 실패로 처리 (크래시 대신 로그만 남김)
            Log.w(TAG, "executor 종료 이후라 클립 합성 제출 실패 (logId=$logId)", e)
            errorCb?.invoke(e)
        }
    }
}
