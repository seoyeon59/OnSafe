package com.example.on_safe.ui.camera

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.FrameLayout
import com.example.on_safe.R
import kotlin.random.Random

/**
 * 카메라 모드 화면보호기 — 무동작 시 자동 dim + 번인 방지(주기적 위치 이동).
 *
 * @param window 밝기 조절 대상
 * @param rootLayout 오버레이를 붙일 루트 뷰
 */
class ScreenSaverController(
    private val window: Window,
    private val rootLayout: FrameLayout
) {
    private val handler = Handler(Looper.getMainLooper())
    private val inactivityRunnable = Runnable { trigger() }
    private val dimRestorationRunnable = Runnable { dim() }
    private val pixelShiftRunnable = Runnable { applyPixelShift() }
    private val clearJustRestoredRunnable = Runnable { justRestoredFromDim = false }

    private var screenSaverView: View? = null
    private var isScreenDimmed = false
    // 어두운 상태에서 첫 터치로 밝기가 복원된 직후 플래그 (btnWakeUp 즉시 해제 방지)
    private var justRestoredFromDim = false

    /** Activity.onUserInteraction()에서 그대로 위임 호출 */
    fun onUserInteraction() {
        if (screenSaverView != null) {
            if (isScreenDimmed) {
                justRestoredFromDim = true
                handler.removeCallbacks(clearJustRestoredRunnable)
                handler.postDelayed(clearJustRestoredRunnable, 300)
            }
            restoreBrightnessTemporarily()
        } else {
            resetInactivityTimer()
        }
    }

    fun resetInactivityTimer() {
        handler.removeCallbacks(inactivityRunnable)
        handler.postDelayed(inactivityRunnable, INACTIVITY_TIMEOUT_MS)
    }

    /** Activity.onPause()에서 호출 — 타이머만 멈추고 화면보호기 표시 상태는 그대로 둔다(원본과 동일) */
    fun pauseTimers() {
        handler.removeCallbacks(inactivityRunnable)
        handler.removeCallbacks(dimRestorationRunnable)
        restoreBrightness()
    }

    /** Activity.onDestroy()에서 호출 — 전체 콜백 정리 */
    fun release() {
        handler.removeCallbacksAndMessages(null)
        justRestoredFromDim = false
    }

    private fun trigger() {
        dim()
        show()
    }

    private fun show() {
        if (screenSaverView != null) return
        val overlay = LayoutInflater.from(rootLayout.context)
            .inflate(R.layout.activity_screen_saver, rootLayout, false)
        overlay.findViewById<View>(R.id.btnWakeUp).setOnClickListener { hide() }
        rootLayout.addView(overlay)
        screenSaverView = overlay
        // 번인 방지: 60초마다 콘텐츠 위치를 ±5px 범위에서 랜덤 이동
        handler.postDelayed(pixelShiftRunnable, PIXEL_SHIFT_INTERVAL_MS)
    }

    private fun hide() {
        // 화면이 어두운 상태에서의 첫 터치로 밝기가 방금 복원된 경우 → 해제 차단
        if (justRestoredFromDim) return
        screenSaverView?.let {
            rootLayout.removeView(it)
            screenSaverView = null
        }
        handler.removeCallbacks(dimRestorationRunnable)
        handler.removeCallbacks(clearJustRestoredRunnable)
        handler.removeCallbacks(pixelShiftRunnable)
        restoreBrightness()
        resetInactivityTimer()
    }

    // 번인 방지 — 화면 보호기 뷰를 살짝 랜덤 이동시키고 다음 이동을 다시 예약
    private fun applyPixelShift() {
        val view = screenSaverView ?: return
        view.translationX = Random.nextInt(-PIXEL_SHIFT_RANGE_PX, PIXEL_SHIFT_RANGE_PX + 1).toFloat()
        view.translationY = Random.nextInt(-PIXEL_SHIFT_RANGE_PX, PIXEL_SHIFT_RANGE_PX + 1).toFloat()
        handler.postDelayed(pixelShiftRunnable, PIXEL_SHIFT_INTERVAL_MS)
    }

    private fun dim() {
        isScreenDimmed = true
        window.attributes = window.attributes.also { it.screenBrightness = BRIGHTNESS_DIM }
    }

    private fun restoreBrightness() {
        isScreenDimmed = false
        handler.removeCallbacks(dimRestorationRunnable)
        window.attributes = window.attributes.also { it.screenBrightness = BRIGHTNESS_SYSTEM }
    }

    // 터치 시 밝기 10초간 복원 후 다시 어둡게 (화면 보호기 유지)
    private fun restoreBrightnessTemporarily() {
        isScreenDimmed = false
        handler.removeCallbacks(dimRestorationRunnable)
        window.attributes = window.attributes.also { it.screenBrightness = BRIGHTNESS_SYSTEM }
        handler.postDelayed(dimRestorationRunnable, BRIGHTNESS_RESTORE_MS)
    }

    companion object {
        private const val INACTIVITY_TIMEOUT_MS = 10 * 60 * 1000L
        private const val BRIGHTNESS_RESTORE_MS = 5_000L   // 터치 후 밝기 유지 시간
        private const val BRIGHTNESS_DIM = 0.01f
        private const val BRIGHTNESS_SYSTEM = -1f
        private const val PIXEL_SHIFT_INTERVAL_MS = 60_000L  // 번인 방지: 60초마다 위치 이동
        private const val PIXEL_SHIFT_RANGE_PX = 5            // ±5px 범위에서 랜덤 이동
    }
}
