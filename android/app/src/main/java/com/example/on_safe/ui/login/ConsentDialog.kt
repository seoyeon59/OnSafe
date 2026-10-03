package com.example.on_safe.ui.login

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
 * 약관 재동의 창
 * - 필수 개정 포함: [로그아웃 | 동의하고 계속], 동의 전 진행 불가. 뒤로가기는 앱 백그라운드 전환(갇힘 방지, 복귀 시 창 유지)
 * - 경미한 개정만: [나중에 | 확인]. 나중에·뒤로가기는 기록 없이 진행, 다음 로그인 시 재표시
 * - 바깥 터치 무반응, 동시에 창 하나
 */
class ConsentDialog private constructor(
    private val activity: AppCompatActivity,
    private var items: List<PendingConsent>,
    private val onDone: () -> Unit
) {
    private val content = activity.layoutInflater.inflate(R.layout.dialog_consent, null)
    private val dialog = cardDialog(activity, content).apply { setCancelable(false) }
    private val layoutItems: LinearLayout = content.findViewById(R.id.layoutConsentItems)
    private val tvTitle: TextView = content.findViewById(R.id.tvConsentDialogTitle)
    private val tvMessage: TextView = content.findViewById(R.id.tvConsentDialogMessage)
    private val btnAgree: TextView = content.findViewById(R.id.btnConsentAgree)
    private val btnDecline: TextView = content.findViewById(R.id.btnConsentDecline)

    // 필수 개정 포함 여부 — 서버 차단 대상
    private val blocking get() = items.any { it.required }

    private fun show() {
        bind()
        btnAgree.setOnClickListener { agree() }
        btnDecline.setOnClickListener { if (blocking) logout() else later() }
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_BACK) return@setOnKeyListener false
            if (event.action == KeyEvent.ACTION_UP) {
                if (blocking) activity.moveTaskToBack(true) else later()
            }
            true
        }
        dialog.setOnDismissListener { current = null }
        // 화면 종료 시 창 정리 — 창 누수·중복 방지 표시 잔존 방지
        activity.lifecycle.addObserver(LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_DESTROY) dialog.dismiss()
        })
        dialog.show()
    }

    private fun bind() {
        tvTitle.text = if (blocking) "약관이 변경되었습니다" else "약관 변경 안내"
        tvMessage.text = if (blocking) "서비스를 계속 이용하려면\n변경된 약관에 동의해주세요."
        else "일부 약관 내용이 변경되었습니다.\n내용을 확인해주세요."
        btnDecline.text = if (blocking) "로그아웃" else "나중에"
        btnAgree.text = agreeLabel()

        layoutItems.removeAllViews()
        items.forEachIndexed { i, item ->
            if (i > 0) {
                layoutItems.addView(
                    View(activity).apply { setBackgroundResource(R.color.border_subtle) },
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

    private fun agreeLabel() = if (blocking) "동의하고 계속" else "확인"

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
                // 창을 띄운 사이 재개정 — 최신 목록 재조회
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

    // 동의 반영된 새 토큰 발급 — 기존 access 토큰에 차단 표시(cr) 잔존
    private suspend fun finish() {
        when (renewTokens(activity)) {
            200 -> {
                dialog.dismiss()
                onDone()
            }
            401 -> {
                dialog.dismiss()
                SessionEvents.expire(activity)
            }
            // 동의는 저장 상태 — 재시도 시 재기록 후 토큰 교체
            else -> activity.toast("잠시 후 다시 시도해주세요.")
        }
    }

    private suspend fun reload(userId: String) {
        val latest = ApiClient.api.getPendingConsents(userId).body()?.data
        if (latest.isNullOrEmpty()) finish() else rebind(latest)
    }

    private fun rebind(latest: List<PendingConsent>) {
        items = latest
        bind()
        activity.toast("약관이 다시 변경되었습니다. 확인 후 동의해주세요.")
    }

    private fun later() {
        dialog.dismiss()
        onDone()
    }

    private fun logout() {
        dialog.dismiss()
        SessionEvents.logout(activity)
    }

    // 요청 중 버튼 잠금 — 동의 저장 중 로그아웃 방지
    private fun setBusy(busy: Boolean) {
        btnAgree.isEnabled = !busy
        btnDecline.isEnabled = !busy
        btnAgree.text = if (busy) "처리 중…" else agreeLabel()
    }

    // 서버 약관 종류 → 표시 이름·약관 페이지
    private fun termsOf(type: String): Pair<String, String> = when (type) {
        "TERMS_OF_SERVICE" -> "이용약관" to TermsLinks.SERVICE
        "PRIVACY_POLICY" -> "개인정보 수집 및 이용" to TermsLinks.PRIVACY
        "SENSITIVE_INFO" -> "민감정보(건강·위치) 처리" to TermsLinks.SENSITIVE
        else -> "약관" to TermsLinks.ALL
    }

    companion object {
        // 표시 중·목록 조회 중 표시 — 403 연속 수신 시 중복 방지
        private var current: ConsentDialog? = null
        private var checking = false

        // 로그인·자동 로그인용 — 목록 보유, 종료 시 [onDone]으로 온보딩
        fun show(activity: AppCompatActivity, items: List<PendingConsent>, onDone: () -> Unit) {
            if (current != null || items.isEmpty()) return
            current = ConsentDialog(activity, items, onDone).also { it.show() }
        }

        /**
         * 사용 중 403 CONSENT_REQUIRED — 현재 화면 위 표시, 화면 전환 없음(카메라 촬영 유지).
         * 대기 목록 없음 = 다른 기기에서 동의 완료 → 차단 표시만 남은 토큰 교체
         */
        fun showForBlocked(activity: AppCompatActivity) {
            if (current != null || checking) return
            checking = true
            activity.lifecycleScope.launch {
                try {
                    val pending = ApiClient.api.getPendingConsents(TokenManager.getUserId(activity))
                        .body()?.data.orEmpty()
                    if (pending.isEmpty()) renewTokens(activity)
                    else show(activity, pending) { activity.toast("약관 동의가 반영되었습니다.") }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // 통신 실패 — 다음 403 수신 시 재시도
                } finally {
                    checking = false
                }
            }
        }
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
