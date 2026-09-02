package com.example.on_safe.util

import android.os.CountDownTimer
import java.util.Locale

/**
 * 이메일 인증코드 3분 카운트다운 (아이디/비밀번호 찾기, 회원가입 2단계 공용).
 * 상태 갱신은 화면마다 필드명이 달라 호출 측 콜백에 맡긴다.
 */
class VerificationCodeTimer(
    private val onTick: (timerText: String) -> Unit,
    private val onFinish: () -> Unit
) {
    private var timer: CountDownTimer? = null

    fun start(durationMs: Long = DEFAULT_DURATION_MS) {
        cancel()
        timer = object : CountDownTimer(durationMs, TICK_INTERVAL_MS) {
            override fun onTick(millisUntilFinished: Long) {
                val totalSeconds = millisUntilFinished / 1_000
                // 로케일 미지정 시 일부 언어에서 아라비아 숫자가 아닌 자형으로 출력됨
                onTick(String.format(Locale.KOREA, "%d:%02d", totalSeconds / 60, totalSeconds % 60))
            }

            override fun onFinish() {
                this@VerificationCodeTimer.onFinish()
            }
        }.start()
    }

    fun cancel() {
        timer?.cancel()
        timer = null
    }

    companion object {
        const val DEFAULT_DURATION_MS = 180_000L
        private const val TICK_INTERVAL_MS = 1_000L
    }
}
