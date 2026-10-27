package com.example.on_safe.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.example.on_safe.network.dto.LandmarkPoint
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * On-device MediaPipe Pose Landmarker(IMAGE) 래퍼.
 * 학습 파이프라인과 동일하게 프레임별 독립 detect()로 좌표를 추출한다(추적/스무딩 없음).
 * 카메라 바인딩은 [CameraModeActivity] 소유 — 여기서는 추론과 [analyze] 콜백만 담당.
 */
class PoseLandmarkerHelper(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onLandmarks(frameIndex: Int, timestampSec: Float, landmarks: List<LandmarkPoint>)
        fun onError(message: String)
    }

    companion object {
        private const val MODEL_ASSET_PATH = "pose_landmarker_lite.task"
        // unbind 반영 전 shutdown() 시 남은 프레임 전달이 RejectedExecutionException 유발 — 유예 필요
        private const val SHUTDOWN_GRACE_MS = 1_000L
    }

    // ImageAnalysis.setAnalyzer()에 넘길 전용 실행기 — 레코딩 세션(start~stop)과 생명주기 동일
    val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var poseLandmarker: PoseLandmarker? = null
    private val frameCounter = AtomicInteger(0)
    @Volatile
    private var startElapsedMs = 0L

    // 프레임 시계(startElapsedMs·frameCounter) 리셋과 프레임 전달을 직렬화 — 분석 스레드 ↔ 메인 스레드
    private val clockLock = Any()

    // 분석 프레임을 함께 받을 곳(실시간 영상 송출) — 분석 스레드에서 회전된 Bitmap을 넘긴다
    @Volatile
    var frameSink: ((Bitmap) -> Unit)? = null

    fun start() {
        startElapsedMs = SystemClock.elapsedRealtime()
        frameCounter.set(0)

        executor.execute {
            try {
                val baseOptions = BaseOptions.builder()
                    .setDelegate(Delegate.CPU)
                    .setModelAssetPath(MODEL_ASSET_PATH)
                    .build()

                // 학습 파이프라인(2.MediaPipe_Pose.ipynb)과 동일하게 IMAGE 모드로 추출한다.
                // 학습은 detect()(프레임 독립, 추적/스무딩 없음)로 좌표를 뽑았으므로, 추론도
                // LIVE_STREAM(추적 적용) 대신 IMAGE 로 맞춰야 좌표 분포가 학습과 일치한다.
                // 신뢰도(0.5)·num_poses(1)도 학습과 동일하게 명시.
                val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.IMAGE)
                    .setNumPoses(1)
                    .setMinPoseDetectionConfidence(0.5f)
                    .setMinPosePresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .build()

                poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            } catch (e: Exception) {
                listener.onError("PoseLandmarker 초기화 실패: ${e.message}")
            }
        }
    }

    /**
     * [ImageAnalysis]의 Analyzer로 등록해 쓰는 프레임 콜백.
     * toBitmap()이 행 여백(rowStride ≠ width*4) 처리 — 버퍼 직접 복사 시 기기별 이미지 어긋남 방지
     */
    fun analyze(imageProxy: ImageProxy) {
        val landmarker = poseLandmarker
        if (landmarker == null) {
            imageProxy.close()
            return
        }

        val bitmapBuffer = imageProxy.toBitmap()
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        imageProxy.close()

        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true
        )
        // 실시간 영상 송출 — 추론과 같은 프레임을 넘긴다(카메라를 따로 열 수 없음).
        // 송출 쪽 오류가 이 분석 스레드로 번지면 포즈 분석·랜드마크 전송이 멈추므로 여기서 끊는다
        try {
            frameSink?.invoke(rotatedBitmap)
        } catch (_: Exception) {
            // 송출은 부가 기능 — 프레임 하나 버리고 추론은 계속
        }

        val mpImage = BitmapImageBuilder(rotatedBitmap).build()
        // IMAGE 모드 동기 추론 — analyze()는 단일 스레드 executor에서 호출되고 CameraX는
        // KEEP_ONLY_LATEST로 중간 프레임을 버리므로, 프레임별 독립 detect()가 학습과 일치한다.
        try {
            handleResult(landmarker.detect(mpImage))
        } catch (e: Exception) {
            listener.onError("포즈 추론 실패: ${e.message}")
        }
    }

    private fun handleResult(result: PoseLandmarkerResult) {
        val poses = result.landmarks()
        if (poses.isEmpty()) return

        val landmarks = poses[0].map { landmark ->
            LandmarkPoint(
                x = landmark.x(),
                y = landmark.y(),
                z = landmark.z(),
                v = landmark.visibility().orElse(0f)
            )
        }

        // timestamp 계산부터 전달(전송)까지를 [resetClock]과 겹치지 않게 한다 — 겹치면 재연결 직후 첫 프레임이
        // 리셋 전 시계(예: 3599.9초)로 나가 서버 윈도우에서 시간이 거꾸로 흐른다
        synchronized(clockLock) {
            val timestampSec = (SystemClock.elapsedRealtime() - startElapsedMs) / 1000f
            listener.onLandmarks(frameCounter.getAndIncrement(), timestampSec, landmarks)
        }
    }

    // WS 재연결마다 호출 — 서버는 init을 받을 때마다 프레임 윈도우를 비우고 연결마다 timestamp를 0부터 받는다.
    // 진행 중인 프레임 전달이 끝난 뒤 리셋한다. 그 프레임은 아직 새 소켓이 열리기 전이라 버려진다
    // (LandmarkStreamClient는 리셋을 부른 뒤에 프레임 전송을 연다).
    fun resetClock() {
        synchronized(clockLock) {
            startElapsedMs = SystemClock.elapsedRealtime()
            frameCounter.set(0)
        }
    }

    fun stop() {
        executor.execute {
            poseLandmarker?.close()
            poseLandmarker = null
        }
        mainHandler.postDelayed({ executor.shutdown() }, SHUTDOWN_GRACE_MS)
    }
}
