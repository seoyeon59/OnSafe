package com.example.on_safe.util

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import com.example.on_safe.messaging.FcmTokenRegistrar
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.LogoutRequest
import com.example.on_safe.ui.login.LoginActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * 세션 종료·전환 전역 처리 — 로그인 화면으로 이동(작업 스택 정리).
 * - expire: 서버가 세션을 끊음(INVALID_TOKEN·refresh 401·WS 1008·비밀번호 변경)
 * - logout: 사용자 로그아웃
 * - requireConsent: 403 CONSENT_REQUIRED — 세션 유지, 로그인 화면의 자동 로그인이 재동의 창 표시
 */
object SessionEvents {

    const val MSG_EXPIRED = "세션이 만료되어 다시 로그인해주세요."

    // 403 CONSENT_REQUIRED로 로그인 화면 진입 표시
    const val EXTRA_CONSENT_REQUIRED = "extra_consent_required"

    // 동시 요청 여러 개가 한꺼번에 실패해도 1회만 처리
    private const val DEDUPE_MS = 5_000L
    private val lastExpiredAt = AtomicLong(0L)
    private val lastConsentAt = AtomicLong(0L)

    fun expire(context: Context, message: String = MSG_EXPIRED) {
        if (!claimOnce(lastExpiredAt)) return
        val app = context.applicationContext
        TokenManager.clearSession(app)
        FcmTokenRegistrar.clearLocal(app)
        goToLogin(app, message)
    }

    fun requireConsent(context: Context) {
        if (!claimOnce(lastConsentAt)) return
        goToLogin(context.applicationContext, "변경된 약관에 동의가 필요합니다.", consentRequired = true)
    }

    /**
     * 로컬 정리·화면 이동 먼저, 서버 로그아웃은 뒤에.
     * 서버 응답을 기다리는 사이 화면이 사라지면 요청이 취소돼 토큰이 남던 문제 방지
     */
    fun logout(context: Context, message: String = "로그아웃 되었습니다.") {
        val app = context.applicationContext
        // 정리 전에 토큰 확보 — 정리 후엔 자동 부착이 비어 access 토큰이 블랙리스트에 안 오름
        val accessToken = TokenManager.getAccessToken(app)
        val refreshToken = TokenManager.getRefreshToken(app)
        // FCM 토큰도 정리 전에 확보 — 서버가 로그아웃과 함께 해제
        val fcm = LogoutRequest(FcmTokenRegistrar.currentToken(app), FcmTokenRegistrar.deviceId(app))
        FcmTokenRegistrar.clearLocal(app)
        TokenManager.clearSession(app)
        goToLogin(app, message)
        AppScope.launch {
            try {
                ApiClient.api.logout(accessToken?.let { "Bearer $it" }, refreshToken, fcm)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 무시 — 로컬은 이미 정리됨
            }
        }
    }

    private fun claimOnce(lastAt: AtomicLong): Boolean {
        val now = SystemClock.elapsedRealtime()
        val prev = lastAt.get()
        if (prev != 0L && now - prev < DEDUPE_MS) return false
        return lastAt.compareAndSet(prev, now)
    }

    // OkHttp 스레드에서도 호출되므로 UI 작업은 메인 스레드로
    private fun goToLogin(app: Context, message: String, consentRequired: Boolean = false) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(app, message, Toast.LENGTH_LONG).show()
            app.startActivity(
                Intent(app, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(EXTRA_CONSENT_REQUIRED, consentRequired)
            )
        }
    }
}
