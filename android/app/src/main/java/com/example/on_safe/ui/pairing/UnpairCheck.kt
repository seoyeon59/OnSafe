package com.example.on_safe.ui.pairing

import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import com.example.on_safe.R
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.isOk
import kotlinx.coroutines.CancellationException

/**
 * 연결 해제 전 확인 — 해제 확인 창을 띄우기 전에 피보호자의 미확인 위험 알림 건수를 조회해 창에 경고를 붙인다.
 * 재알림은 발송 시점의 보호자에게 가므로, 미확인 위험이 남은 채 해제하면 이후 재알림이 보호자에게 가지 않는다.
 * 보호자(홈)·피보호자(카메라 모드) 양쪽 해제 창이 함께 쓴다(dialog_unpair.xml).
 */
object UnpairCheck {

    /**
     * 미확인 위험 알림 건수. 조회 실패면 null — 해제를 막지 않도록 경고 없이 기존 확인 창을 띄운다.
     */
    suspend fun unconfirmedDangerCount(userId: String, counterpartUserId: String): Int? = try {
        val response = ApiClient.api.getUnpairCheck(userId, counterpartUserId)
        if (response.isOk) response.body()?.data?.unconfirmedDangerCount else null
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /**
     * 해제 창(dialog_unpair.xml)에 경고 표시 — 1건 이상일 때만 보인다.
     * @param byGuardian 보호자가 해제하는 창이면 true(문구의 주어가 "나"), 피보호자면 false("보호자")
     */
    fun bindWarning(root: View, count: Int?, byGuardian: Boolean) {
        val warning = root.findViewById<TextView>(R.id.tvUnpairDangerWarning) ?: return
        bindWarning(warning, count, byGuardian)
    }

    fun bindWarning(warning: TextView, count: Int?, byGuardian: Boolean) {
        val visible = count != null && count > 0
        warning.isVisible = visible
        if (!visible) return
        val consequence = if (byGuardian) {
            "해제하면 더 이상 위험 알림을 받을 수 없습니다."
        } else {
            "해제하면 보호자에게 더 이상 알림이 가지 않습니다."
        }
        warning.text = "확인되지 않은 위험 알림이 ${count}건 있습니다.\n$consequence"
    }
}
