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

    /**
     * [ImageAnalysis]의 Analyzer로 등록해 쓰는 프레임 콜백.
     * TODO: planes[0].rowStride가 width*4와 다른 기기에서 이미지가 어긋날 수 있음 — 실기기 확인 필요.
     */
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

    // input은 MediaPipe 콜백 시그니처상 필수 — 사용하지 않음
    @Suppress("UNUSED_PARAMETER")
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
