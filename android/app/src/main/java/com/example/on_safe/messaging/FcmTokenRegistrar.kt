package com.example.on_safe.messaging

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.example.on_safe.BuildConfig
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.FcmTokenRequest
import com.example.on_safe.network.isOk
import com.example.on_safe.util.TokenManager
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * FCM 등록 토큰을 서버에 등록·해제한다.
 *
 * 서버는 이 토큰으로 승인/거부/오프라인 등 실시간 이벤트를 발송한다. 토큰은
 *  1) 로그인 성공 직후,
 *  2) 자동 로그인(앱 시작 시 이미 로그인 상태),
 *  3) Firebase 가 토큰을 새로 발급([OnSafeMessagingService.onNewToken])
 * 시점에 동기화하고, 로그아웃/탈퇴 시 해제한다.
 *
 * google-services.json 미설정(=Firebase 미초기화) 환경에서도 크래시 없이 no-op 이 되도록
 * 모든 Firebase 접근을 try/catch 로 감싼다.
 */
object FcmTokenRegistrar {

    private const val TAG = "FcmToken"
    private const val PREFS = "fcm"
    private const val KEY_SYNCED_TOKEN = "synced_token"

    // 화면 수명과 무관한 fire-and-forget 네트워크 작업 전용 스코프.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 로그인 상태일 때만 현재 토큰을 조회해 서버와 동기화. 앱 시작·로그인 직후 호출. */
    fun registerIfLoggedIn(context: Context) {
        if (!TokenManager.isLoggedIn(context)) return
        val appContext = context.applicationContext

        val messaging = try {
            FirebaseMessaging.getInstance()
        } catch (e: Exception) {
            // google-services.json 이 없으면 여기서 걸린다 — FCM 비활성 상태로 조용히 종료.
            if (BuildConfig.DEBUG) Log.w(TAG, "Firebase 미초기화 — 토큰 등록 건너뜀", e)
            return
        }
        messaging.token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                if (BuildConfig.DEBUG) Log.w(TAG, "FCM 토큰 조회 실패", task.exception)
                return@addOnCompleteListener
            }
            task.result?.let { syncToServer(appContext, it, force = false) }
        }
    }

    /** Firebase 가 새 토큰을 발급한 경우 — 캐시와 반드시 달라 강제 동기화한다. */
    fun onNewToken(context: Context, token: String) {
        syncToServer(context.applicationContext, token, force = true)
    }

    /**
     * 로그아웃·회원탈퇴 시 호출.
     * 세션이 곧 정리되므로 userId·토큰을 지금(동기) 확보한 뒤, 서버 해제는 best-effort 로 시도한다.
     * 핵심은 이 기기가 더는 푸시를 받지 않도록 Firebase 토큰 자체를 폐기하는 것.
     */
    fun unregister(context: Context) {
        val appContext = context.applicationContext
        val token = syncedToken(appContext)
        val userId = TokenManager.getUserId(appContext)
        clearSyncedToken(appContext)

        scope.launch {
            if (!token.isNullOrBlank() && userId.isNotBlank()) {
                try {
                    ApiClient.api.deleteFcmToken(userId, FcmTokenRequest(token, deviceId(appContext)))
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "서버 토큰 해제 실패(무시)", e)
                }
            }
            // 다음 로그인 사용자가 이전 토큰으로 알림을 받는 일이 없도록 기기 토큰을 폐기.
            try {
                FirebaseMessaging.getInstance().deleteToken()
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Firebase 토큰 폐기 실패(무시)", e)
            }
        }
    }

    // userId 가 있어야(로그인 상태) 등록 가능. 없으면 로그인 시점에 registerIfLoggedIn 으로 재시도된다.
    private fun syncToServer(appContext: Context, token: String, force: Boolean) {
        scope.launch {
            val userId = TokenManager.getUserId(appContext)
            if (userId.isBlank()) return@launch
            // 동일 토큰 반복 전송 방지 — 새 토큰(force)일 때만 무조건 보낸다.
            if (!force && token == syncedToken(appContext)) return@launch
            try {
                val response = ApiClient.api.registerFcmToken(
                    userId, FcmTokenRequest(token, deviceId(appContext))
                )
                if (response.isOk) {
                    saveSyncedToken(appContext, token)
                } else if (BuildConfig.DEBUG) {
                    Log.w(TAG, "서버 토큰 등록 실패 — HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "서버 토큰 등록 네트워크 오류", e)
            }
        }
    }

    // 로그인 요청과 동일한 식별자 — 같은 계정의 여러 기기를 서버가 구분하도록 함께 보낸다.
    private fun deviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun syncedToken(context: Context): String? =
        prefs(context).getString(KEY_SYNCED_TOKEN, null)

    private fun saveSyncedToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_SYNCED_TOKEN, token).apply()
    }

    private fun clearSyncedToken(context: Context) {
        prefs(context).edit().remove(KEY_SYNCED_TOKEN).apply()
    }
}
