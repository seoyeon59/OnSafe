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
import com.example.on_safe.ui.FullscreenActivity
import com.example.on_safe.ui.history.AccidentHistoryActivity
import com.example.on_safe.ui.notification.NotificationActivity
import com.example.on_safe.ui.settings.SettingsActivity
import com.example.on_safe.util.DisplayText
import com.example.on_safe.util.DoubleBackToExit
import com.example.on_safe.util.NotificationPermissionBanner
import com.example.on_safe.util.RiskScoreCardBinder
import com.example.on_safe.util.TokenManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    private var alertDialog: BottomSheetDialog? = null


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
    }

    override fun onResume() {
        super.onResume()
        NotificationPermissionBanner.refresh(this)
        viewModel.startPolling(TokenManager.getUserId(this))
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
        // 사고이력: 왼쪽 탭 → 왼쪽에서 슬라이드 인
        // 홈은 finish() 없이 유지 — 사고이력·설정에서 뒤로가기 시 홈 복귀
        findViewById<View>(R.id.tabHistory).setOnClickListener {
            startActivity(Intent(this, AccidentHistoryActivity::class.java))
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        // 설정: 오른쪽 탭 → 오른쪽에서 슬라이드 인
        findViewById<View>(R.id.tabSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        findViewById<View>(R.id.btn119).setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:119")))
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

    // 점수 미수신 사유별 문구 분기
    private fun riskUnknownMessage(state: ConnectionState): String = when (state) {
        ConnectionState.STANDBY -> "카메라 기기가 연결되면 표시됩니다."
        ConnectionState.FAILED -> "위험 지수를 불러오지 못했습니다."
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
