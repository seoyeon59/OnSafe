package com.example.on_safe

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.on_safe.network.ApiClient
import com.example.on_safe.ui.notification.NotificationActivity
import com.example.on_safe.util.NotificationPermissionBanner
import com.example.on_safe.util.RiskScoreCardBinder
import com.example.on_safe.util.TokenManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    private var pollingJob: Job? = null

    // DANGER 진입 시점에만 모달을 띄우기 위해 직전 위험 등급을 기억
    private var lastRiskLevel: RiskScoreCardBinder.RiskLevel? = null

    // 홈에서 뒤로가기 두 번 눌러야 종료 (실수로 바로 꺼지는 것 방지)
    private var backPressedTime = 0L

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

        applyConnectionState(ConnectionState.CONNECTING)

        NotificationPermissionBanner.setup(this)
        setupClickListeners()

        // 홈은 탭 이동의 종착점 — 뒤로가기를 두 번 눌러야 앱이 종료되도록 확인 단계를 둠
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (System.currentTimeMillis() - backPressedTime < 2000) {
                    finish()
                } else {
                    backPressedTime = System.currentTimeMillis()
                    Toast.makeText(this@MainActivity, "한 번 더 누르면 종료됩니다.", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        NotificationPermissionBanner.refresh(this)
        startRiskScorePolling()
    }

    override fun onPause() {
        super.onPause()
        stopRiskScorePolling()
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
            overridePendingTransition(R.anim.detail_enter, R.anim.detail_exit)
        }
        findViewById<View>(R.id.btnFullscreen).setOnClickListener {
            startActivity(Intent(this, com.example.on_safe.ui.FullscreenActivity::class.java))
            overridePendingTransition(R.anim.fullscreen_enter, R.anim.fullscreen_exit)
        }
        // 사고이력: 왼쪽 탭 → 왼쪽에서 슬라이드 인
        // 홈은 finish()하지 않고 그대로 둠 → 뒤로가기를 누르면 사고이력/설정에서 홈으로 돌아옴
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
    }

    // 5초 간격 위험 점수 폴링
    private fun startRiskScorePolling() {
        stopRiskScorePolling()

        val userId = TokenManager.getUserId(this)
        if (userId.isBlank()) {
            applyConnectionState(ConnectionState.STANDBY)
            return
        }

        pollingJob = lifecycleScope.launch {
            while (isActive) {
                fetchRiskScoreOnce(userId)
                delay(POLLING_INTERVAL_MS)
            }
        }
    }

    private fun stopRiskScorePolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private suspend fun fetchRiskScoreOnce(userId: String) {
        try {
            val response = ApiClient.api.getRiskScore(userId)
            val body = response.body()
            if (response.isSuccessful && body?.success == true && body.data != null) {
                val score = body.data.score.toInt().coerceIn(0, 100)
                applyRiskScore(score)
                applyConnectionState(ConnectionState.CONNECTED)
            } else {
                applyConnectionState(ConnectionState.FAILED)
            }
        } catch (_: Exception) {
            applyConnectionState(ConnectionState.FAILED)
        }
    }

    private fun applyRiskScore(score: Int) {
        val mainCard = findViewById<View>(R.id.riskScoreCard)
        RiskScoreCardBinder.bind(mainCard, score)

        val currentLevel = RiskScoreCardBinder.RiskLevel.fromScore(score)
        // 이전 등급이 DANGER 미만이었다가 DANGER로 진입한 경우에만 모달 표시
        if (currentLevel == RiskScoreCardBinder.RiskLevel.DANGER &&
            lastRiskLevel != RiskScoreCardBinder.RiskLevel.DANGER
        ) {
            showFallAlertDialog(score, System.currentTimeMillis())
        }
        lastRiskLevel = currentLevel
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

    companion object {
        private const val POLLING_INTERVAL_MS = 5_000L
    }
}