package com.example.on_safe.ui.login

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.on_safe.R
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.ConsentAgreeItem
import com.example.on_safe.network.dto.ConsentAgreeRequest
import com.example.on_safe.network.dto.PendingConsent
import com.example.on_safe.network.failure
import com.example.on_safe.network.isOk
import com.example.on_safe.util.SessionEvents
import com.example.on_safe.util.TermsLinks
import com.example.on_safe.util.TokenManager
import com.example.on_safe.util.cardDialog
import com.example.on_safe.util.openTermsUrl
import com.example.on_safe.util.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 필수 약관 재동의 창 — 뒤로가기·바깥 터치로 안 닫힘.
 * 동의 → 서버 기록 → 남은 목록 없으면 토큰 교체(차단 표시 cr 제거) 후 [onAgreed].
 * 거부 → 로그아웃
 */
class ConsentDialog(
    private val activity: AppCompatActivity,
    private var items: List<PendingConsent>,
    private val onAgreed: () -> Unit
) {
    private val content = activity.layoutInflater.inflate(R.layout.dialog_consent, null)
    private val dialog = cardDialog(activity, content).apply { setCancelable(false) }
    private val layoutItems: LinearLayout = content.findViewById(R.id.layoutConsentItems)
    private val btnAgree: TextView = content.findViewById(R.id.btnConsentAgree)

    fun show() {
        bindItems()
        btnAgree.setOnClickListener { agree() }
        content.findViewById<TextView>(R.id.btnConsentDecline).setOnClickListener {
            dialog.dismiss()
            SessionEvents.logout(activity)
        }
        dialog.show()
    }

    private fun bindItems() {
        layoutItems.removeAllViews()
        items.forEachIndexed { i, item ->
            if (i > 0) {
                layoutItems.addView(
                    View(activity).apply {
                        setBackgroundResource(R.color.border_subtle)
                    },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                )
            }
            val (title, url) = termsOf(item.type)
            val row = activity.layoutInflater.inflate(R.layout.item_consent_row, layoutItems, false)
            row.findViewById<TextView>(R.id.tvConsentTitle).text = title
            row.findViewById<TextView>(R.id.tvConsentTag).apply {
                // 경미한 개정은 확인만 — 필수와 색으로 구분
                text = if (item.required) "(필수)" else "(변경 안내)"
                setTextColor(activity.getColor(if (item.required) R.color.primary_blue else R.color.ink_500))
            }
            row.setOnClickListener { activity.openTermsUrl(url) }
            layoutItems.addView(row)
        }
    }

    private fun agree() {
        val userId = TokenManager.getUserId(activity)
        setBusy(true)
        activity.lifecycleScope.launch {
            try {
                val response = ApiClient.api.agreeConsents(
                    userId,
                    ConsentAgreeRequest(items.map { ConsentAgreeItem(it.type, it.version) })
                )
                if (response.isOk) {
                    val remaining = response.body()?.data.orEmpty()
                    if (remaining.isEmpty()) finish() else rebind(remaining)
                    return@launch
                }
                val failure = response.failure("약관 동의에 실패했습니다.")
                // 창을 띄운 사이 재개정 — 최신 목록으로 다시 받기
                if (failure.code == "CONSENT_VERSION_MISMATCH") reload(userId)
                else activity.toast(failure.message)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                activity.toast("네트워크 오류가 발생했습니다.")
            } finally {
                setBusy(false)
            }
        }
    }

    // 동의 반영된 새 토큰 발급 — 기존 access 토큰엔 차단 표시가 남아 있음
    private suspend fun finish() {
        when (renewTokens(activity)) {
            200 -> {
                dialog.dismiss()
                onAgreed()
            }
            401 -> {
                dialog.dismiss()
                SessionEvents.expire(activity)
            }
            // 동의는 저장됨 — 다시 누르면 재기록 후 토큰 교체 재시도
            else -> activity.toast("잠시 후 다시 시도해주세요.")
        }
    }

    private suspend fun reload(userId: String) {
        val latest = ApiClient.api.getPendingConsents(userId).body()?.data
        if (latest.isNullOrEmpty()) finish() else rebind(latest)
    }

    private fun rebind(latest: List<PendingConsent>) {
        items = latest
        bindItems()
        activity.toast("약관이 다시 변경되었습니다. 확인 후 동의해주세요.")
    }

    private fun setBusy(busy: Boolean) {
        btnAgree.isEnabled = !busy
        btnAgree.text = if (busy) "처리 중…" else "동의하고 계속"
    }

    // 서버 약관 종류 → 표시 이름·약관 페이지
    private fun termsOf(type: String): Pair<String, String> = when (type) {
        "TERMS_OF_SERVICE" -> "이용약관" to TermsLinks.SERVICE
        "PRIVACY_POLICY" -> "개인정보 수집 및 이용" to TermsLinks.PRIVACY
        "SENSITIVE_INFO" -> "민감정보(건강·위치) 처리" to TermsLinks.SENSITIVE
        else -> "약관" to TermsLinks.ALL
    }
}

/**
 * refresh 토큰으로 토큰 교체 — 재동의 후 차단 표시(cr) 제거용.
 * 반환: 성공 200, 그 외 HTTP 코드(토큰 없음은 401 취급). 통신 실패는 예외 그대로
 */
internal suspend fun renewTokens(context: Context): Int {
    val refreshToken = TokenManager.getRefreshToken(context)
    if (refreshToken.isNullOrBlank()) return 401
    val response = ApiClient.api.refresh(refreshToken)
    val tokens = response.body()?.data
    if (!response.isOk || tokens == null) return response.code()
    TokenManager.updateAccessToken(context, tokens.accessToken, tokens.refreshToken)
    return 200
}
