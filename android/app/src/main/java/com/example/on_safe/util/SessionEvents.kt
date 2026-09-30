package com.example.on_safe.util

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import com.example.on_safe.messaging.FcmTokenRegistrar
import com.example.on_safe.ui.login.LoginActivity
import java.util.concurrent.atomic.AtomicLong

/**
 * 서버가 세션을 끊었을 때(INVALID_TOKEN·refresh 401·WS 1008·비밀번호 변경) 전역 처리.
 * 로컬 세션·FCM 캐시 정리 후 안내와 함께 로그인 화면으로 이동(작업 스택 정리).
 */
object SessionEvents {

    const val MSG_EXPIRED = "세션이 만료되어 다시 로그인해주세요."

    // 동시 요청 여러 개가 한꺼번에 401을 받아도 1회만 처리
    private const val DEDUPE_MS = 5_000L
    private val lastExpiredAt = AtomicLong(0L)

    fun expire(context: Context, message: String = MSG_EXPIRED) {
        val now = SystemClock.elapsedRealtime()
        val prev = lastExpiredAt.get()
        if (prev != 0L && now - prev < DEDUPE_MS) return
        if (!lastExpiredAt.compareAndSet(prev, now)) return

        val app = context.applicationContext
        TokenManager.clearSession(app)
        FcmTokenRegistrar.clearLocal(app)
        // OkHttp 스레드에서도 호출되므로 UI 작업은 메인 스레드로
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(app, message, Toast.LENGTH_LONG).show()
            app.startActivity(
                Intent(app, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
        }
    }
}
