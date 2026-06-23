package com.example.on_safe.util

import android.content.Context

// 인증 토큰 및 사용자 정보 중앙 관리 (SharedPreferences "auth")
object TokenManager {

    private const val PREFS_NAME     = "auth"
    private const val KEY_ACCESS     = "access_token"
    private const val KEY_REFRESH    = "refresh_token"
    private const val KEY_USER_ID    = "user_id"

    fun saveTokens(context: Context, accessToken: String, refreshToken: String, userId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_ACCESS,  accessToken)
            .putString(KEY_REFRESH, refreshToken)
            .putString(KEY_USER_ID, userId)
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
}
