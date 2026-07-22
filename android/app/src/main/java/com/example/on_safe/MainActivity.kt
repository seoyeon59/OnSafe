package com.example.on_safe

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.on_safe.ui.notification.NotificationActivity
import com.example.on_safe.util.NotificationPermissionBanner
import com.example.on_safe.util.RiskScoreCardBinder
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // 연결 상태
    private enum class ConnectionState(val label: String, val colorRes: Int) {
        CONNECTED("기기 연결됨", R.color.status_normal),
        CONNECTING("연결중...", R.color.status_warning),
        FAILED("기기 연결 실패", R.color.status_danger),
        STANDBY("대기 중", R.color.status_standby)
    }

    private var alertDialog: BottomSheetDialog? = null

    // TODO: WebSocket/SSE 연결 후 실시간 점수로 교체 (현재 테스트 더미)
    private val testScores = intArrayOf(32, 62, 88)
    private var testIdx = 0

    // 알림 화면에서 돌아올 때 미읽음 여부에 따라 빨간 점 갱신
    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val hasUnread = result.data?.getBooleanExtra(NotificationActivity.EXTRA_HAS_UNREAD, true) ?: true
        findViewById<View>(R.id.dotUnreadNotification)?.visibility =
            if (hasUnread) View.VISIBLE else View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // TODO: 서버 실시간 점수/연결 상태로 교체
        val mainCard = findViewById<View>(R.id.riskScoreCard)
        RiskScoreCardBinder.bind(mainCard, 32)
        applyConnectionState(ConnectionState.CONNECTED)

        NotificationPermissionBanner.setup(this)
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        NotificationPermissionBanner.refresh(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 모달이 열린 채로 Activity가 소멸되면 WindowLeak 발생 → 명시적으로 dismiss
        alertDialog?.dismiss()
        alertDialog = null
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.btnNotification).setOnClickListener {
            notificationLauncher.launch(Intent(this, NotificationActivity::class.java))
        }
        findViewById<View>(R.id.btnFullscreen).setOnClickListener {
            startActivity(Intent(this, com.example.on_safe.ui.FullscreenActivity::class.java))
        }
        // 사고이력: 왼쪽 탭 → 왼쪽에서 슬라이드 인
        findViewById<View>(R.id.tabHistory).setOnClickListener {
            startActivity(Intent(this, com.example.on_safe.ui.history.AccidentHistoryActivity::class.java))
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        // 설정: 오른쪽 탭 → 오른쪽에서 슬라이드 인
        findViewById<View>(R.id.tabSettings).setOnClickListener {
            startActivity(Intent(this, com.example.on_safe.ui.settings.SettingsActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        findViewById<View>(R.id.btn119).setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:119")))
        }

        // [테스트] 점수 박스 길게 누르면 점수 순환 (실제 서버 연결 후 제거)
        val mainCard = findViewById<View>(R.id.riskScoreCard)
        mainCard.findViewById<View>(R.id.tvRiskScore).setOnLongClickListener {
            testIdx = (testIdx + 1) % testScores.size
            val score = testScores[testIdx]
            RiskScoreCardBinder.bind(mainCard, score)

            // 위험 단계면 그 시점 점수 + 현재 시각으로 모달 표시
            if (RiskScoreCardBinder.RiskLevel.fromScore(score) == RiskScoreCardBinder.RiskLevel.DANGER) {
                showFallAlertDialog(score, System.currentTimeMillis())
            }
            true
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
            (view.parent as? android.view.View)?.background = null
        }
        dialog.show()
        alertDialog = dialog
    }

}