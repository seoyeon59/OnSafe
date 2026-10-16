package com.example.on_safe.util

import android.content.Context
import androidx.core.content.edit

/**
 * 이 기기에서 로그인한 계정이 "보호자와 연결된 피보호자"인지 기억한다.
 *
 * 서버 규칙상 한 계정은 보호자·피보호자 중 하나로만 페어링된다. 카메라 모드가 my-guardian 조회로
 * 연결을 확정할 때마다 기록하고, 낙상 알림 수신 시 data에 user_id가 없으면 이 값으로 피보호자 기기를 판별한다.
 * 계정별로 저장해 같은 기기에서 다른 계정으로 로그인해도 섞이지 않는다.
 */
object PairingRole {

    private const val PREFS = "pairing_role"
    private const val KEY_PREFIX = "paired_elder:"

    fun setPairedAsElder(context: Context, userId: String, paired: Boolean) {
        if (userId.isBlank()) return
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_PREFIX + userId, paired) }
    }

    fun isPairedAsElder(context: Context, userId: String): Boolean {
        if (userId.isBlank()) return false
        return context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PREFIX + userId, false)
    }
}
