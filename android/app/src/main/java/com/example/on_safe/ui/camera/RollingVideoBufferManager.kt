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
        private const val PRE_EVENT_SEGMENTS = 8       // 약 2분
        private const val POST_EVENT_SEGMENTS = 8      // 약 2분
        // 사고 후 분량을 찍는 동안 사고 전 세그먼트가 밀려 지워지지 않게 둘의 합만큼 보관(약 4분).
        // 예전 12개(3분)는 합(16)보다 작아, 촬영 1분 이후의 사고는 합성 시 앞 세그먼트가 없어 클립이 실패했다
        private const val RING_BUFFER_CAPACITY = PRE_EVENT_SEGMENTS + POST_EVENT_SEGMENTS
        // 점수는 30프레임(~1초) 윈도우를 2초 구간으로 평활화한 값이라 실제 움직임보다 늦게 최고점에 닿는다 — 그만큼 앞당긴다
        private const val SCORE_LAG_MS = 1_000L
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
    private var onClipReady: ((File, Long?) -> Unit)? = null
    private var onClipError: ((Throwable) -> Unit)? = null

    // 클립을 만드는 동안 위험 점수가 가장 높았던 값·시각(벽시계 ms) — 보호자 앱 썸네일 위치
    private var peakScore = 0f
    private var peakAtMs = 0L

    // 사고 후 분량을 다 찍기 전에 촬영이 끝남 — 마지막 세그먼트의 Finalize(성공·실패 모두)에서
    // 지금까지 찍힌 분량으로 클립을 마무리한다. 사고 순간이 그 세그먼트에 있을 수 있어 기다린다
    private var finishCaptureOnStop = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val rotateRunnable = Runnable { rotateSegment() }

    fun start() {
        synchronized(this) {
            // 직전 정지의 마지막 세그먼트를 기다리는 중에 다시 시작 — 기다리지 않고 지금 분량으로 마무리
            if (finishCaptureOnStop) {
                finishCaptureOnStop = false
                finishCaptureLocked(deleteSources = true)
            }
        }
        running = true
        rotateSegment()
    }

    /**
     * 촬영 정지. 위험 클립을 만드는 중(사고 후 분량을 찍는 중)이면 버리지 않고 지금까지 찍힌 분량으로
     * 마무리한다 — 예전에는 사고 직후 촬영을 끄면 클립이 통째로 사라져 사고 영상이 저장되지 않았다.
     */
    fun stop() {
        running = false
        mainHandler.removeCallbacks(rotateRunnable)
        val lastRecording = currentRecording
        currentRecording = null
        synchronized(this) {
            // 진행 중인 클립에 쓸 세그먼트는 남기고(합성 뒤 지운다) 나머지 링 버퍼만 비운다
            val keep = if (capturingLogId != null) (preSegmentsSnapshot + postSegments).toSet() else emptySet()
            segments.filterNot { it in keep }.forEach { it.delete() }
            segments.clear()
            if (capturingLogId != null) {
                // 녹화 중이던 세그먼트가 있으면 그 Finalize에서 마무리(아래 stop() 전에 표시해야 경쟁이 없다)
                if (lastRecording != null) finishCaptureOnStop = true
                else finishCaptureLocked(deleteSources = true)
            }
        }
        lastRecording?.stop()
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
                    // SOURCE_INACTIVE(카메라 해제로 끝남)는 그때까지의 파일이 유효하다 — 촬영 정지 직후 마지막 세그먼트
                    val usable = !event.hasError() ||
                        (event.error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE && file.length() > 0)
                    if (usable) {
                        onSegmentFinalized(file)
                    } else {
                        Log.w(TAG, "세그먼트 녹화 오류: errorCode=${event.error}", event.cause)
                        file.delete()
                        onLastSegmentLost()
                    }
                }
            }
        mainHandler.postDelayed(rotateRunnable, SEGMENT_DURATION_MS)
    }

    private fun onSegmentFinalized(file: File) {
        // stop() 뒤에 마무리된 마지막 세그먼트 — 진행 중이던 클립이 있으면 넣어 마무리, 아니면 버린다
        if (!running) {
            synchronized(this) {
                if (finishCaptureOnStop) {
                    finishCaptureOnStop = false
                    postSegments.add(file)
                    finishCaptureLocked(deleteSources = true)
                    return
                }
            }
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

    /**
     * 위험 이벤트 시 호출 — 진행 중인 스플라이스가 있으면 무시.
     * [onReady]의 두 번째 값은 클립 시작부터 위험 점수가 가장 높았던 시점(ms) — 계산할 수 없으면 null
     */
    fun captureDangerClip(
        logId: String,
        score: Float,
        onReady: (File, Long?) -> Unit,
        onError: (Throwable) -> Unit
    ) {
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
            peakScore = score
            peakAtMs = System.currentTimeMillis()
        }
    }

    /** 추론 결과마다 호출 — 클립을 만드는 중이면 최고점과 그 시각을 갱신한다 */
    fun reportScore(score: Float) {
        synchronized(this) {
            if (capturingLogId == null || finishCaptureOnStop) return
            if (score > peakScore) {
                peakScore = score
                peakAtMs = System.currentTimeMillis()
            }
        }
    }

    // 세그먼트 파일명(seg_{번호}_{시작 벽시계 ms}.mp4)에서 녹화 시작 시각
    private fun segmentStartMs(file: File): Long? =
        file.nameWithoutExtension.substringAfterLast('_').toLongOrNull()

    // 정지 후 마지막 세그먼트가 오류로 끝남 — 그 세그먼트 없이 지금 분량으로 마무리
    private fun onLastSegmentLost() {
        if (running) return
        synchronized(this) {
            if (!finishCaptureOnStop) return
            finishCaptureOnStop = false
            finishCaptureLocked(deleteSources = true)
        }
    }

    // 호출부가 이미 synchronized(this) 안이라 별도 락 불필요.
    // [deleteSources]: 촬영 정지로 링 버퍼가 비워진 뒤라 원본 세그먼트를 합성 후 직접 지운다
    private fun finishCaptureLocked(deleteSources: Boolean = false) {
        val logId = capturingLogId ?: return
        val pre = preSegmentsSnapshot
        val post = postSegments.toList()
        val readyCb = onClipReady
        val errorCb = onClipError
        // 클립 첫 세그먼트 시작부터 최고점까지. 세그먼트 사이 틈(수십 ms)은 무시한다
        val clipStartMs = (pre + post).firstOrNull()?.let(::segmentStartMs)
        val peakOffsetMs = clipStartMs?.let { (peakAtMs - SCORE_LAG_MS - it).coerceAtLeast(0L) }

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
                    readyCb?.invoke(outputFile, peakOffsetMs)
                } catch (e: Exception) {
                    Log.w(TAG, "클립 합성 실패 (logId=$logId)", e)
                    errorCb?.invoke(e)
                } finally {
                    if (deleteSources) (pre + post).forEach { it.delete() }
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
