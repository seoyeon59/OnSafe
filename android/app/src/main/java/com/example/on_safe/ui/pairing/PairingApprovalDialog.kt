package com.example.on_safe.ui.pairing

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.example.on_safe.OnSafeApp
import com.example.on_safe.R
import com.example.on_safe.messaging.PushEvent
import com.example.on_safe.messaging.PushEventBus
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.ApiResponse
import com.example.on_safe.network.failure
import com.example.on_safe.network.isOk
import com.example.on_safe.ui.login.LoginActivity
import com.example.on_safe.ui.login.PermissionActivity
import com.example.on_safe.ui.tutorial.TutorialActivity
import com.example.on_safe.util.TokenManager
import com.example.on_safe.util.cardDialog
import com.example.on_safe.util.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.Response
import java.lang.ref.WeakReference

/** 서버 pairing_request 푸시 data — 요청 목록 조회 API가 없어 request_id 는 이 푸시가 유일한 출처 */
data class PairingRequest(
    val requestId: String,
    val guardianName: String,
    // 승인 시 교체될 현재 보호자 — 없으면 null
    val displacedGuardianName: String?,
    val sentAtMillis: Long,
)

/**
 * 보호자 연결 요청 승인 창 (피보호자용)
 *
 * 보호자가 페어링 코드를 입력하면 서버가 피보호자에게 pairing_request 푸시를 보낸다(30분 유효).
 * 관계는 피보호자가 여기서 승인해야 성립한다.
 * - 포그라운드 수신: [offer] — 메시징 서비스가 호출, 현재 화면 위에 바로 표시
 * - 백그라운드 수신: 시스템이 띄운 알림 탭 → 런처 인텐트 extras → [offerFromIntent]
 * 보이는 화면이 없거나 로그인 전 화면이면 보관했다가 다음 화면 진입([showPendingOn]) 때 표시.
 * 승인·거절 전에는 닫히지 않는다 — 만료되면 보관분도 버린다.
 */
object PairingApprovalDialog {

    const val EVENT = "pairing_request"

    // 앱 내부 전용(서버 이벤트 아님) — 승인 성립을 화면에 알린다.
    // "pairing" 접두라 페어링 갱신 구독자(카메라 모드 등)가 함께 받는다
    const val EVENT_APPROVED_LOCAL = "pairing_request_approved"

    // 서버 PAIRING_REQUEST_TTL(30분)과 동일
    private const val REQUEST_TTL_MS = 30 * 60 * 1000L
    private const val KEY_SENT_TIME = "google.sent_time"

    private val mainHandler = Handler(Looper.getMainLooper())

    // 아래 상태는 모두 메인 스레드에서만 다룬다
    private var pending: PairingRequest? = null
    private var shown: Shown? = null

    // 화면은 약한 참조 — 정적 보관이 Activity 를 붙잡지 않도록(창은 화면 종료 때 닫힘)
    private class Shown(activity: Activity, val request: PairingRequest, val dialog: Dialog) {
        private val activityRef = WeakReference(activity)
        fun isOn(activity: Activity) = activityRef.get() === activity
    }

    /** 포그라운드 FCM 수신(서비스 스레드) */
    fun offer(data: Map<String, String>, sentAtMillis: Long) {
        val request = parse(data::get, sentAtMillis) ?: return
        mainHandler.post {
            pending = request
            OnSafeApp.resumed?.get()?.let { showPendingOn(it) }
        }
    }

    /** 알림 탭으로 열린 화면의 인텐트 — 화면 재생성 때 다시 받지 않도록 표식을 지운다 */
    fun offerFromIntent(intent: Intent?) {
        if (intent?.getStringExtra("event") != EVENT) return
        val sentAt = intent.getLongExtra(KEY_SENT_TIME, 0L)
        val request = parse(intent::getStringExtra, sentAt)
        intent.removeExtra("event")
        if (request != null) pending = request
    }

    /** 화면 진입마다 호출 — 보관 중인 요청이 있으면 이 화면 위에 표시 */
    fun showPendingOn(activity: Activity) {
        val request = pending ?: return
        if (System.currentTimeMillis() - request.sentAtMillis > REQUEST_TTL_MS) {
            pending = null
            return
        }
        if (activity !is AppCompatActivity || !isEligible(activity)) return
        shown?.let {
            if (it.isOn(activity) && it.request == request) return
            it.dialog.dismiss()
        }
        show(activity, request)
    }

    // 로그인 전·온보딩 화면은 곧 사라지거나 승인 API 를 부를 세션이 없다 — 다음 화면에서 표시
    private fun isEligible(activity: AppCompatActivity): Boolean =
        !activity.isFinishing &&
            TokenManager.isLoggedIn(activity) &&
            activity !is LoginActivity &&
            activity !is TutorialActivity &&
            activity !is PermissionActivity

    private fun parse(get: (String) -> String?, sentAtMillis: Long): PairingRequest? {
        val requestId = get("request_id")?.takeIf { it.isNotBlank() } ?: return null
        return PairingRequest(
            requestId = requestId,
            guardianName = get("guardian_name")?.takeIf { it.isNotBlank() } ?: "보호자",
            displacedGuardianName = get("displaces_guardian_name")?.takeIf { it.isNotBlank() },
            // 발송 시각을 모르면 받은 시각으로 — 0 이면 바로 만료 처리돼 버린다
            sentAtMillis = sentAtMillis.takeIf { it > 0 } ?: System.currentTimeMillis(),
        )
    }

    private fun show(activity: AppCompatActivity, request: PairingRequest) {
        val content = activity.layoutInflater.inflate(R.layout.dialog_pairing_request, null)
        val dialog = cardDialog(activity, content).apply { setCancelable(false) }
        val btnApprove: TextView = content.findViewById(R.id.btnPairingApprove)
        val btnReject: TextView = content.findViewById(R.id.btnPairingReject)

        content.findViewById<TextView>(R.id.tvPairingRequestMessage).text =
            "${request.guardianName}님이 보호자로 연결을 요청했어요."
        content.findViewById<TextView>(R.id.tvPairingRequestDisplaces).apply {
            isVisible = request.displacedGuardianName != null
            text = "승인하면 현재 보호자 ${request.displacedGuardianName}님과의 연결이 해제됩니다."
        }

        fun setBusy(busy: Boolean) {
            btnApprove.isEnabled = !busy
            btnReject.isEnabled = !busy
            btnApprove.text = if (busy) "처리 중…" else "승인"
        }

        fun respond(approve: Boolean) {
            val userId = TokenManager.getUserId(activity)
            setBusy(true)
            activity.lifecycleScope.launch {
                try {
                    val outcome =
                        if (approve) ApiClient.api.approvePairingRequest(userId, request.requestId).toOutcome()
                        else ApiClient.api.rejectPairingRequest(userId, request.requestId).toOutcome()
                    when (outcome) {
                        Outcome.Done -> {
                            finish(request)
                            if (approve) {
                                activity.toast("${request.guardianName}님과 보호자 연결이 완료되었어요.")
                                PushEventBus.publish(PushEvent(EVENT_APPROVED_LOCAL, emptyMap()))
                            } else {
                                activity.toast("연결 요청을 거절했어요.")
                            }
                        }
                        Outcome.Retry -> {
                            activity.toast("잠시 후 다시 시도해주세요.")
                            setBusy(false)
                        }
                        is Outcome.Gone -> {
                            finish(request)
                            activity.toast(outcome.message)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    activity.toast("네트워크 오류가 발생했어요.")
                    setBusy(false)
                }
            }
        }

        btnApprove.setOnClickListener { respond(approve = true) }
        btnReject.setOnClickListener { respond(approve = false) }

        // 화면 종료 시 창만 정리 — 요청은 보관해 다음 화면에서 다시 표시
        val destroyObserver = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_DESTROY) dialog.dismiss()
        }
        activity.lifecycle.addObserver(destroyObserver)
        val entry = Shown(activity, request, dialog)
        dialog.setOnDismissListener {
            activity.lifecycle.removeObserver(destroyObserver)
            if (shown === entry) shown = null
        }
        shown = entry
        dialog.show()
    }

    private sealed interface Outcome {
        object Done : Outcome
        // 5xx(Redis 장애 등)는 요청 소비 전 실패 — 창을 두고 재시도 가능
        object Retry : Outcome
        // 4xx 는 서버가 이미 요청을 소비(GETDEL)했거나 만료 — 다시 눌러도 같은 결과
        class Gone(val message: String) : Outcome
    }

    // 승인·거절 응답 타입이 달라 공통 판정으로 모은다
    private fun <T> Response<ApiResponse<T>>.toOutcome(): Outcome = when {
        isOk -> Outcome.Done
        code() >= 500 -> Outcome.Retry
        else -> {
            val failure = failure("연결 요청을 처리하지 못했어요.")
            Outcome.Gone(
                if (failure.code == "PAIRING_REQUEST_INVALID") "요청이 만료되었거나 이미 처리되었어요."
                else failure.message
            )
        }
    }

    // 응답 완료 — 그 사이 새 요청이 들어왔으면 그것은 남긴다
    private fun finish(request: PairingRequest) {
        if (pending == request) pending = null
        shown?.takeIf { it.request == request }?.dialog?.dismiss()
    }
}
