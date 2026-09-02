package com.example.on_safe.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.on_safe.BuildConfig

/**
 * 인증 토큰·사용자 정보 중앙 관리.
 * EncryptedSharedPreferences로 암호화 저장 — 루팅/ADB 접근에도 원문이 노출되지 않는다.
 */
object TokenManager {

    private const val PREFS_NAME = "auth_secure"
    private const val KEY_ACCESS = "access_token"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_LOGIN_TIME = "login_time"

    // SettingsActivity·EditProfileActivity가 알림·마케팅 설정을 캐시하는 파일
    private const val SETTINGS_PREFS = "settings"

    // 마지막 로그인으로부터 30일 이상 경과 시 재인증 요구
    private const val SESSION_DURATION_MS = 30L * 24 * 60 * 60 * 1000

    // Keystore 접근 비용 때문에 인스턴스 캐싱
    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        return cachedPrefs ?: synchronized(this) {
            cachedPrefs ?: createEncryptedPrefs(context.applicationContext).also { cachedPrefs = it }
        }
    }

    /**
     * 암호화 저장소 생성.
     * 백업 복원·Keystore 초기화 시 기존 키셋 복호화 불가로 생성 자체가 실패 —
     * 마스터 키가 백업 대상이 아니라 기기 이전 시 필연. 손상 파일 폐기 후 1회 재생성.
     */
    private fun createEncryptedPrefs(context: Context): SharedPreferences =
        try {
            buildEncryptedPrefs(context)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w("TokenManager", "암호화 저장소 복호화 불가 — 재생성", e)
            context.deleteSharedPreferences(PREFS_NAME)
            buildEncryptedPrefs(context)
        }

    private fun buildEncryptedPrefs(context: Context): SharedPreferences {
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

    // 실제 로그인 전용 — login_time을 현재 시각으로 찍어 30일 만료 기준점 갱신
    fun saveTokens(context: Context, accessToken: String, refreshToken: String, userId: String) {
        prefs(context).edit {
            putString(KEY_ACCESS, accessToken)
            putString(KEY_REFRESH, refreshToken)
            putString(KEY_USER_ID, userId)
            putLong(KEY_LOGIN_TIME, System.currentTimeMillis())
        }
    }

    // 401 자동 갱신 전용 — login_time을 함께 갱신하면 "마지막 로그인 30일" 정책이 무력화되므로 제외
    fun updateAccessToken(context: Context, accessToken: String, refreshToken: String) {
        prefs(context).edit {
            putString(KEY_ACCESS, accessToken)
            putString(KEY_REFRESH, refreshToken)
        }
    }

    fun getAccessToken(context: Context): String? =
        prefs(context).getString(KEY_ACCESS, null)

    fun getRefreshToken(context: Context): String? =
        prefs(context).getString(KEY_REFRESH, null)

    fun getUserId(context: Context): String =
        prefs(context).getString(KEY_USER_ID, "") ?: ""

    fun clear(context: Context) {
        prefs(context).edit { clear() }
        // 캐시 무효화 — 다음 접근 시 재초기화
        // (EncryptedSharedPreferences는 clear()에서 키셋을 남기므로 파일 자체는 그대로 재사용됨)
        cachedPrefs = null
    }

    // 로그아웃·회원탈퇴 공통 — 토큰과 함께 화면 설정 캐시까지 정리.
    // 남겨두면 같은 기기에 다른 계정이 로그인했을 때 이전 사용자의 설정이 보인다.
    fun clearSession(context: Context) {
        clear(context)
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE).edit { clear() }
    }

    fun isLoggedIn(context: Context): Boolean = getAccessToken(context) != null

    /**
     * 마지막 로그인으로부터 SESSION_DURATION_MS(30일) 이상 경과했으면 true.
     * 로그인 기록이 없는 경우에도 true(만료 처리).
     */
    fun isSessionExpired(context: Context): Boolean {
        val lastLogin = prefs(context).getLong(KEY_LOGIN_TIME, 0L)
        if (lastLogin == 0L) return true
        return System.currentTimeMillis() - lastLogin > SESSION_DURATION_MS
    }
}
