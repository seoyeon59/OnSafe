package com.example.on_safe.ui.pairing

import android.graphics.Typeface
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.on_safe.R
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.isOk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 연결 해제 전 확인 — 해제 확인 창을 띄우기 전에 피보호자의 미확인 위험 알림 건수를 조회해 창에 경고를 붙인다.
 * 재알림은 발송 시점의 보호자에게 가므로, 미확인 위험이 남은 채 해제하면 이후 재알림이 보호자에게 가지 않는다.
 * 보호자(홈)·피보호자(카메라 모드) 양쪽 해제 창이 함께 쓴다(dialog_unpair.xml).
 */
object UnpairCheck {

    /** 해제 창은 바로 띄우고, 미확인 위험 건수는 조회되는 대로 [warning]에 표시 (보호자·피보호자 창 공용) */
    fun loadWarning(scope: CoroutineScope, warning: TextView, userId: String, counterpartUserId: String, byGuardian: Boolean) {
        scope.launch { bindWarning(warning, unconfirmedDangerCount(userId, counterpartUserId), byGuardian) }
    }

    /**
     * 미확인 위험 알림 건수. 조회 실패면 null — 해제는 막지 않고 창에 중립 안내만 붙인다([bindWarning]).
     */
    private suspend fun unconfirmedDangerCount(userId: String, counterpartUserId: String): Int? = try {
        val response = ApiClient.api.getUnpairCheck(userId, counterpartUserId)
        if (response.isOk) response.body()?.data?.unconfirmedDangerCount else null
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /**
     * 해제 창(dialog_unpair.xml)의 안내 줄.
     * - 1건 이상: 빨간 경고
     * - 0건: 표시 없음
     * - 조회 실패(null): 회색 중립 안내 — 0건과 구분해, 미확인 위험이 남았을 수 있음을 알린다(해제는 막지 않음)
     * @param byGuardian 보호자가 해제하는 창이면 true(문구의 주어가 "나"), 피보호자면 false("보호자")
     */
    private fun bindWarning(warning: TextView, count: Int?, byGuardian: Boolean) {
        when {
            count == null -> {
                warning.isVisible = true
                warning.setTextColor(ContextCompat.getColor(warning.context, R.color.ink_500))
                warning.setTypeface(null, Typeface.NORMAL)
                warning.text = "미확인 위험 알림 여부를 확인하지 못했습니다."
            }
            count > 0 -> {
                warning.isVisible = true
                warning.setTextColor(ContextCompat.getColor(warning.context, R.color.status_danger))
                warning.setTypeface(null, Typeface.BOLD)
                val consequence = if (byGuardian) {
                    "해제하면 더 이상 위험 알림을 받을 수 없습니다."
                } else {
                    "해제하면 보호자에게 더 이상 알림이 가지 않습니다."
                }
                warning.text = "확인되지 않은 위험 알림이 ${count}건 있습니다.\n$consequence"
            }
            else -> warning.isVisible = false
        }
    }
}
