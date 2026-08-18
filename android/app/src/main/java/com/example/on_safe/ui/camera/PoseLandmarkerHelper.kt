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
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * On-device MediaPipe Pose Landmarker(LIVE_STREAM) 래퍼.
 * 카메라 바인딩은 [CameraModeActivity]가 소유하므로(`startCamera()`), 이 클래스는
 * MediaPipe 처리와 [ImageAnalysis.Analyzer]로 넘길 [analyze] 콜백만 담당한다.
 */
class PoseLandmarkerHelper(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onLandmarks(frameIndex: Int, timestampSec: Float, landmarks: List<LandmarkPoint>)
        fun onError(message: String)
    }

    companion object {
        private const val MODEL_ASSET_PATH = "pose_landmarker_lite.task"
        // stop() 호출 시점에도 CameraX가 이미 큐에 든 프레임을 이 executor로 계속 전달 중일 수
        // 있다. unbind가 완전히 반영되기 전에 shutdown()하면 그 전달이 RejectedExecutionException을
        // 던질 수 있어, 유예 시간을 두고 종료한다. (CameraModeActivity 쪽에서도 카메라 unbind를
        // stop()보다 먼저 호출하도록 순서를 맞춰 실제로 겹칠 여지 자체를 줄여둔다.)
        private const val SHUTDOWN_GRACE_MS = 1_000L
    }

    // ImageAnalysis.setAnalyzer()에 그대로 넘겨 쓸 전용 실행기 — 레코딩 세션(start~stop)과 생명주기 동일
    val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var poseLandmarker: PoseLandmarker? = null
    private val frameCounter = AtomicInteger(0)
    private var startElapsedMs = 0L

    fun start() {
        startElapsedMs = SystemClock.elapsedRealtime()
        frameCounter.set(0)

        executor.execute {
            try {
                val baseOptions = BaseOptions.builder()
                    .setDelegate(Delegate.CPU)
                    .setModelAssetPath(MODEL_ASSET_PATH)
                    .build()

                val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener(::onLivestreamResult)
                    .setErrorListener { error -> listener.onError(error.message ?: "MediaPipe 오류") }
                    .build()

                poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            } catch (e: Exception) {
                listener.onError("PoseLandmarker 초기화 실패: ${e.message}")
            }
        }
    }

    /** [ImageAnalysis]의 Analyzer로 그대로 등록해서 쓰는 프레임 콜백 */
    fun analyze(imageProxy: ImageProxy) {
        val landmarker = poseLandmarker
        if (landmarker == null) {
            imageProxy.close()
            return
        }

        val bitmapBuffer = Bitmap.createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
        bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer)
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        imageProxy.close()

        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true
        )

        val mpImage = BitmapImageBuilder(rotatedBitmap).build()
        landmarker.detectAsync(mpImage, SystemClock.uptimeMillis())
    }

    private fun onLivestreamResult(result: PoseLandmarkerResult, input: MPImage) {
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

        val timestampSec = (SystemClock.elapsedRealtime() - startElapsedMs) / 1000f
        listener.onLandmarks(frameCounter.getAndIncrement(), timestampSec, landmarks)
    }

    fun stop() {
        executor.execute {
            poseLandmarker?.close()
            poseLandmarker = null
        }
        mainHandler.postDelayed({ executor.shutdown() }, SHUTDOWN_GRACE_MS)
    }
}
