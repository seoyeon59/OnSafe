package com.example.on_safe.util

import android.os.CountDownTimer

/**
 * 이메일 인증코드 화면들(아이디 찾기, 비밀번호 찾기, 회원가입 2단계)이 공통으로 쓰던
 * 3분 카운트다운 타이머를 하나로 뽑아낸 것. 화면마다 상태 클래스 필드명이 달라서
 * (isRequestCodeEnabled / isEmailVerifyEnabled 등) 상태 갱신 자체는 호출 측 콜백에 맡기고,
 * 이 클래스는 타이머 동작(틱마다 "m:ss" 텍스트 생성, 종료 시점 알림)만 담당한다.
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
