package com.example.on_safe.util

import android.os.CountDownTimer

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
        timer?.cancel()
        timer = object : CountDownTimer(durationMs, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 60_000
                val seconds = (millisUntilFinished % 60_000) / 1_000
                onTick(String.format("%d:%02d", minutes, seconds))
            }

            override fun onFinish() {
                this@VerificationCodeTimer.onFinish()
            }
        }.start()
    }

    fun cancel() {
        timer?.cancel()
    }

    companion object {
        const val DEFAULT_DURATION_MS = 180_000L
    }
}
