package com.example.on_safe.util

import android.content.Context

// 인증 토큰 및 사용자 정보 중앙 관리 (SharedPreferences "auth")
object TokenManager {

    private const val PREFS_NAME          = "auth"
    private const val KEY_ACCESS          = "access_token"
    private const val KEY_REFRESH         = "refresh_token"
    private const val KEY_USER_ID         = "user_id"
    private const val KEY_LOGIN_TIME      = "login_time"
    // 마지막 로그인으로부터 30일 이상 경과 시 재인증 요구
    private const val SESSION_DURATION_MS = 30L * 24 * 60 * 60 * 1000

    fun saveTokens(context: Context, accessToken: String, refreshToken: String, userId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_ACCESS,     accessToken)
            .putString(KEY_REFRESH,    refreshToken)
            .putString(KEY_USER_ID,    userId)
            .putLong(KEY_LOGIN_TIME,   System.currentTimeMillis())
            .apply()
    }

    fun getAccessToken(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACCESS, null)

    fun getRefreshToken(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_REFRESH, null)

    fun getUserId(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USER_ID, "") ?: ""

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun isLoggedIn(context: Context): Boolean = getAccessToken(context) != null

    /**
     * 마지막 로그인으로부터 SESSION_DURATION_MS(30일) 이상 경과했으면 true
     * 로그인 기록이 없는 경우에도 true(만료 처리)
     */
    fun isSessionExpired(context: Context): Boolean {
        val lastLogin = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LOGIN_TIME, 0L)
        if (lastLogin == 0L) return true
        return System.currentTimeMillis() - lastLogin > SESSION_DURATION_MS
    }
}
