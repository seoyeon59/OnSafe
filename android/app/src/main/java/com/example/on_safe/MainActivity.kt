package com.example.on_safe

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.isOk
import com.example.on_safe.ui.FullscreenActivity
import com.example.on_safe.ui.main.GuardianPairingDialogFragment
import com.example.on_safe.ui.notification.NotificationActivity
import com.example.on_safe.util.DisplayText
import com.example.on_safe.util.NavTab
import com.example.on_safe.util.setupBottomNav
import com.example.on_safe.util.DoubleBackToExit
import com.example.on_safe.util.NotificationPermissionBanner
import com.example.on_safe.util.RiskScoreCardBinder
import com.example.on_safe.util.TokenManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PAIRING_TAG = "guardian_pair"
private const val STATE_PAIRED = "paired"
private const val STATE_PAIRING_DEFERRED = "pairing_deferred"

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    private var alertDialog: BottomSheetDialog? = null

    // 연결이 확인됐거나 사용자가 미룬 상태 — 홈에 머무는 동안 재확인·재표시를 막는다.
    // 화면 재생성에도 유지돼야 미뤄둔 모달이 다시 뜨지 않는다(onSaveInstanceState 참고).
    private var isPaired = false
    private var pairingDeferred = false

    // 페어링된 피보호자의 userId — 해제 API 호출에 필요. isPaired=true 인 동안만 유효.
    private var pairedWardUserId: String? = null
    private var pairedWardName: String? = null

    // 조회가 날아가 있는 동안 다시 부르면 같은 모달이 두 개 뜰 수 있다
    private var pairingCheckJob: Job? = null

    // 알림 화면 복귀 시 네트워크 왕복 없는 즉시 반영
    // (뒤이어 onResume의 refreshUnreadBadge가 서버 값으로 재동기화)
    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // 결과 미수신(취소 등)을 미읽음으로 단정 금지 — 빈 목록에 빨간점 표시 문제
        val hasUnread = result.data?.getBooleanExtra(NotificationActivity.EXTRA_HAS_UNREAD, false) ?: false
        viewModel.setUnreadBadge(hasUnread)
    }

    // 미읽음 알림 유무에 따라 종 아이콘과 빨간 점을 함께 갱신
    private fun updateNotificationBell(hasUnread: Boolean) {
        findViewById<View>(R.id.dotUnreadNotification)?.visibility =
            if (hasUnread) View.VISIBLE else View.GONE
        findViewById<ImageView>(R.id.ivNotificationBell)?.setImageResource(
            if (hasUnread) R.drawable.ic_notification_ringing else R.drawable.ic_notification
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        applyConnectionState(ConnectionState.CONNECTING)

        NotificationPermissionBanner.setup(this)
        setupClickListeners()

        // 홈은 탭 이동의 종착점 — 뒤로가기 2회로 종료
        DoubleBackToExit.attach(this)

        observeViewModel()

        savedInstanceState?.let {
            isPaired = it.getBoolean(STATE_PAIRED)
            pairingDeferred = it.getBoolean(STATE_PAIRING_DEFERRED)
        }

        // 모달이 화면 재생성 뒤에 결과를 돌려줘도 받을 수 있도록 항상 등록한다.
        supportFragmentManager.setFragmentResultListener(
            GuardianPairingDialogFragment.REQUEST_KEY, this
        ) { _, result ->
            when {
                // 실제 관계 성립 완료(FCM pairing_approved 이후 리트리거되는 경로).
                result.getBoolean(GuardianPairingDialogFragment.RESULT_PAIRED) -> {
                    isPaired = true
                    viewModel.startPolling(TokenManager.getUserId(this))
                }
                // 요청 전송됨 — 승인 대기 상태. 이번 방문 동안은 모달 재표시 안 하되, 다음 진입에서
                // getWards 로 성립 여부 재판정한다(승인되면 hasWards=true 로 자동 반영).
                result.getBoolean(GuardianPairingDialogFragment.RESULT_REQUEST_SENT) -> {
                    pairingDeferred = true
                }
                // "나중에 하기" — 이번 방문 동안은 다시 묻지 않는다
                else -> pairingDeferred = true
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_PAIRED, isPaired)
        outState.putBoolean(STATE_PAIRING_DEFERRED, pairingDeferred)
    }

    // 진입 시 한 번만이 아니라 홈이 다시 보일 때마다 확인한다 — 오프라인으로 판정을
    // 건너뛴 뒤 통신이 회복돼도 앱을 껐다 켜야만 모달이 뜨던 문제 때문.
    private fun checkGuardianPairingOnEntry() {
        if (isPaired || pairingDeferred) return
        if (pairingCheckJob?.isActive == true) return
        if (supportFragmentManager.findFragmentByTag(PAIRING_TAG) != null) return
        val userId = TokenManager.getUserId(this)
        if (userId.isBlank()) return
        pairingCheckJob = lifecycleScope.launch {
            val wards = try {
                val response = ApiClient.api.getWards(userId)
                // 서버가 답을 주지 못한 경우(401·5xx)도 판정 불가로 본다 — 세션이 끊긴
                // 상태에서 모달을 띄우면 코드를 넣어도 계속 실패한다.
                if (!response.isOk) return@launch
                response.body()?.data?.wards.orEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 네트워크 오류 시엔 모달 강제 표시하지 않음 — 사용자가 오프라인 상태에서도
                // 홈은 볼 수 있어야 함. 통신이 회복되면 다음 onResume에서 재판정.
                return@launch
            }
            if (wards.isNotEmpty()) {
                isPaired = true
                // 1:1 정책상 최대 1건. 해제 버튼에서 counterpart 로 쓴다.
                pairedWardUserId = wards.first().userId
                pairedWardName = wards.first().name
                findViewById<View>(R.id.btnUnpairMain).visibility = View.VISIBLE
                return@launch
            }
            // 응답이 늦게 오면 이미 onSaveInstanceState를 지났을 수 있다.
            // 그 상태에서 show()는 commit이라 IllegalStateException으로 죽는다.
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                GuardianPairingDialogFragment().show(supportFragmentManager, PAIRING_TAG)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        NotificationPermissionBanner.refresh(this)
        viewModel.startPolling(TokenManager.getUserId(this))
        checkGuardianPairingOnEntry()
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopPolling()
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            applyConnectionState(state.connectionState)
            findViewById<TextView>(R.id.tvDeviceId).text = DisplayText.deviceIdLabel(state.deviceId)
            updateNotificationBell(state.hasUnread)
            val card = findViewById<View>(R.id.riskScoreCard)
            if (state.riskScore != null) {
                RiskScoreCardBinder.bind(card, state.riskScore)
            } else {
                // TODO: [UI] 상태별 문구는 riskUnknownMessage가 구분하지만 시각적 로딩 표시가 없어,
                //       최초 진입·네트워크 지연 시 화면이 멈춘 것처럼 보인다. 알림 기록·사고이력처럼
                //       로딩 인디케이터와 빈 상태를 갖출 것. 페어링 방식 변경으로 이 화면의 표시
                //       내용이 달라질 예정이라 그 정리 후 착수.
                RiskScoreCardBinder.bindUnknown(card, riskUnknownMessage(state.connectionState))
            }
        }
        viewModel.fallAlertEvent.observe(this) { event ->
            if (event != null) {
                showFallAlertDialog(event.score, event.detectedAtMillis)
                viewModel.onFallAlertHandled()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 모달 유지 상태의 Activity 소멸 시 WindowLeak — 명시적 dismiss
        alertDialog?.dismiss()
        alertDialog = null
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.btnNotification).setOnClickListener {
            notificationLauncher.launch(Intent(this, NotificationActivity::class.java))
            overridePendingTransition(R.anim.detail_enter, R.anim.detail_exit)
        }
        findViewById<View>(R.id.btnFullscreen).setOnClickListener {
            startActivity(Intent(this, FullscreenActivity::class.java))
            overridePendingTransition(R.anim.fullscreen_enter, R.anim.fullscreen_exit)
        }
        setupBottomNav(NavTab.HOME)
        findViewById<View>(R.id.btn119).setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:119")))
        }
        findViewById<View>(R.id.btnUnpairMain).setOnClickListener { showUnpairDialog() }
    }

    private fun showUnpairDialog() {
        val counterpart = pairedWardUserId
        if (counterpart.isNullOrBlank()) {
            android.widget.Toast.makeText(this, "연결 정보를 확인할 수 없어요.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val view = layoutInflater.inflate(R.layout.dialog_unpair, null, false)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(view)
            .create()
        view.findViewById<View>(R.id.btnUnpairCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.btnUnpairConfirm).setOnClickListener {
            dialog.dismiss()
            performUnpair(counterpart)
        }
        dialog.show()
    }

    private fun performUnpair(counterpartUserId: String) {
        val userId = TokenManager.getUserId(this)
        if (userId.isBlank()) return
        lifecycleScope.launch {
            try {
                val response = ApiClient.api.unpair(userId, counterpartUserId)
                if (response.isOk) {
                    // 해제 성공 — 로컬 상태 초기화, 홈 재조회로 모달 재표시 유도.
                    isPaired = false
                    pairedWardUserId = null
                    pairedWardName = null
                    findViewById<View>(R.id.btnUnpairMain).visibility = View.GONE
                    android.widget.Toast.makeText(this@MainActivity, "피보호자 연결이 해제되었어요.", android.widget.Toast.LENGTH_SHORT).show()
                    // pairingDeferred 는 이번 방문 동안 재확인만 억제하는 값이라 그대로 두면 홈에 계속 남는다.
                    // 명시 해제 후엔 다음 진입 때 페어링 모달이 다시 뜨도록 리셋.
                    pairingDeferred = false
                } else {
                    android.widget.Toast.makeText(this@MainActivity, "연결 해제에 실패했어요.", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                android.widget.Toast.makeText(this@MainActivity, "네트워크 오류가 발생했어요.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyConnectionState(state: ConnectionState) {
        val color = ContextCompat.getColor(this, state.colorRes)
        val dot = findViewById<View>(R.id.connectionDot)
        val tv = findViewById<TextView>(R.id.tvConnectionStatus)
        // backgroundTintList 사용 — 공유 bg_circle Drawable 인스턴스 직접 변조 방지
        dot.backgroundTintList = ColorStateList.valueOf(color)
        tv.text = state.label
        tv.setTextColor(color)
    }

    // 점수 미수신 사유별 문구 분기 — 사용자가 취할 조치가 달라 상태별로 구분
    private fun riskUnknownMessage(state: ConnectionState): String = when (state) {
        ConnectionState.STANDBY -> "카메라 기기가 연결되면 표시됩니다."
        ConnectionState.FAILED -> "위험 지수를 불러오지 못했습니다."
        ConnectionState.INFERENCE_ERROR -> "낙상 감지 일시 중단 — 카메라 상태를 확인해주세요."
        ConnectionState.SLOW -> "낙상 감지 처리 지연 중 — 잠시 후 다시 확인해주세요."
        ConnectionState.RECONNECTING -> "연결 재확인 중 — 잠시만 기다려주세요."
        else -> "위험 지수를 확인하는 중입니다."
    }

    // 위험 감지 바텀시트 — 감지 시점의 점수·시각 스냅샷 표시
    private fun showFallAlertDialog(score: Int, detectedAtMillis: Long) {
        if (alertDialog?.isShowing == true) return

        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_fall_alert, null)

        // 감지 시각 텍스트
        val timeFormat = SimpleDateFormat("a hh:mm", Locale.KOREAN)
        view.findViewById<TextView>(R.id.tvDetectedTime).text =
            "감지 시각 · ${timeFormat.format(Date(detectedAtMillis))}"

        // 모달 안의 점수 카드 (감지 시점 스냅샷, 이후 갱신 없음)
        val alertCard = view.findViewById<View>(R.id.alertRiskScoreCard)
        RiskScoreCardBinder.bind(alertCard, score)

        view.findViewById<View>(R.id.btn119Alert).setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:119")))
        }
        view.findViewById<View>(R.id.btnAlertDismiss).setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.setOnShowListener {
            // Material BottomSheetDialog 기본 둥근 배경 제거 → XML drawable 곡률만 표시
            (view.parent as? View)?.background = null
        }
        dialog.show()
        alertDialog = dialog
    }
}
