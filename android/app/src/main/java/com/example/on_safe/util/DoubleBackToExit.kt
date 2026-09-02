package com.example.on_safe.util

import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/**
 * 뒤로가기를 두 번 눌러야 앱이 종료되도록 처리한다.
 * 실수로 한 번 눌러 앱이 꺼지는 것을 막기 위한 공통 동작 (홈·로그인 화면에서 사용).
 */
object DoubleBackToExit {

    private const val INTERVAL_MS = 2000L

    fun attach(activity: AppCompatActivity) {
        var lastPressed = 0L
        activity.onBackPressedDispatcher.addCallback(
            activity,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val now = System.currentTimeMillis()
                    if (now - lastPressed < INTERVAL_MS) {
                        activity.finishAffinity()
                    } else {
                        lastPressed = now
                        activity.toast("한 번 더 누르면 종료됩니다.")
                    }
                }
            }
        )
    }
}
