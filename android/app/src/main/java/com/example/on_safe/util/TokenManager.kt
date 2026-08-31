package com.example.on_safe.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 인증 토큰·사용자 정보 중앙 관리.
 * EncryptedSharedPreferences로 암호화 저장 — 루팅/ADB 접근에도 원문이 노출되지 않는다.
 */
object TokenManager {

    private const val PREFS_NAME          = "auth_secure"
    private const val KEY_ACCESS          = "access_token"
    private const val KEY_REFRESH         = "refresh_token"
    private const val KEY_USER_ID         = "user_id"
    private const val KEY_LOGIN_TIME      = "login_time"
    // 마지막 로그인으로부터 30일 이상 경과 시 재인증 요구
    private const val SESSION_DURATION_MS = 30L * 24 * 60 * 60 * 1000

    // EncryptedSharedPreferences 초기화(Keystore 접근) 비용 때문에 인스턴스를 캐싱한다
    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        return cachedPrefs ?: synchronized(this) {
            cachedPrefs ?: createEncryptedPrefs(context.applicationContext).also { cachedPrefs = it }
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    // ─────────────────────────────────────────────────────────────────────────

    // 실제 로그인 시에만 호출 — login_time을 현재 시각으로 찍어 30일 세션 만료 기준점을 갱신함
    fun saveTokens(context: Context, accessToken: String, refreshToken: String, userId: String) {
        prefs(context).edit()
            .putString(KEY_ACCESS,   accessToken)
            .putString(KEY_REFRESH,  refreshToken)
            .putString(KEY_USER_ID,  userId)
            .putLong(KEY_LOGIN_TIME, System.currentTimeMillis())
            .apply()
    }

    // 401 자동 갱신 전용 — login_time을 갱신하면 "마지막 로그인 30일" 정책이 무력화되므로 건드리지 않는다
    fun updateAccessToken(context: Context, accessToken: String, refreshToken: String) {
        prefs(context).edit()
            .putString(KEY_ACCESS,  accessToken)
            .putString(KEY_REFRESH, refreshToken)
            .apply()
    }

    fun getAccessToken(context: Context): String? =
        prefs(context).getString(KEY_ACCESS, null)

    fun getRefreshToken(context: Context): String? =
        prefs(context).getString(KEY_REFRESH, null)

    fun getUserId(context: Context): String =
        prefs(context).getString(KEY_USER_ID, "") ?: ""

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
        // 캐시 무효화 — 다음 접근 시 새 파일로 재초기화
        cachedPrefs = null
    }

    fun isLoggedIn(context: Context): Boolean = getAccessToken(context) != null

    /**
     * 마지막 로그인으로부터 SESSION_DURATION_MS(30일) 이상 경과했으면 true
     * 로그인 기록이 없는 경우에도 true(만료 처리)
     */
    fun isSessionExpired(context: Context): Boolean {
        val lastLogin = prefs(context).getLong(KEY_LOGIN_TIME, 0L)
        if (lastLogin == 0L) return true
        return System.currentTimeMillis() - lastLogin > SESSION_DURATION_MS
    }
}
