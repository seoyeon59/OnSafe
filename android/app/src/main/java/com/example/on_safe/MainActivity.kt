package com.example.on_safe

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // 위험 단계
    private enum class RiskLevel(
        val label: String,
        val rangeText: String,
        val message: String,
        val colorRes: Int
    ) {
        NORMAL("정상", "위험 지수 0~50", "어르신이 안정적인 상태입니다.", R.color.status_normal),
        WARNING("주의", "위험 지수 51~75", "어르신의 움직임에 주의가 필요합니다.", R.color.status_warning),
        DANGER("위험", "위험 지수 76~100", "낙상이 의심됩니다. 즉시 확인이 필요합니다.", R.color.status_danger);

        companion object {
            fun fromScore(score: Int): RiskLevel = when {
                score <= 50 -> NORMAL
                score <= 75 -> WARNING
                else -> DANGER
            }
        }
    }

    // 연결 상태
    private enum class ConnectionState(val label: String, val colorRes: Int) {
        CONNECTED("기기 연결됨", R.color.status_normal),
        CONNECTING("연결중...", R.color.status_warning),
        FAILED("기기 연결 실패", R.color.status_danger),
        STANDBY("대기 중", R.color.status_standby)
    }

    private var alertDialog: BottomSheetDialog? = null

    // 테스트용
    private val testScores = intArrayOf(32, 62, 88)
    private var testIdx = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 메인 화면의 위험 지수 카드는 실시간 갱신
        val mainCard = findViewById<View>(R.id.riskScoreCard)
        applyRiskScoreToCard(mainCard, 32)
        applyConnectionState(ConnectionState.CONNECTED)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.btnNotification).setOnClickListener {
            startActivity(Intent(this, com.example.on_safe.ui.notification.NotificationActivity::class.java))
        }
        findViewById<View>(R.id.btnFullscreen).setOnClickListener {
            startActivity(Intent(this, com.example.on_safe.ui.FullscreenActivity::class.java))
        }
        findViewById<View>(R.id.tabHistory).setOnClickListener {
            Toast.makeText(this, "사고 이력 준비 중", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.tabSettings).setOnClickListener {
            startActivity(Intent(this, com.example.on_safe.ui.settings.SettingsActivity::class.java))
        }

        // [테스트] 점수 박스 길게 누르면 점수 순환 (실제 서버 연결 후 제거)
        val mainCard = findViewById<View>(R.id.riskScoreCard)
        mainCard.findViewById<View>(R.id.tvRiskScore).setOnLongClickListener {
            testIdx = (testIdx + 1) % testScores.size
            val score = testScores[testIdx]
            applyRiskScoreToCard(mainCard, score)

            // 위험 단계면 그 시점 점수 + 현재 시각으로 모달 표시
            if (RiskLevel.fromScore(score) == RiskLevel.DANGER) {
                showFallAlertDialog(score, System.currentTimeMillis())
            }
            true
        }
    }

    /**
     * 위험 지수 카드(include 사용 위치 어디든) 갱신.
     * 위험 단계면 빨강 stroke 추가, 아니면 제거.
     */
    private fun applyRiskScoreToCard(cardRoot: View, score: Int) {
        val level = RiskLevel.fromScore(score)
        val color = ContextCompat.getColor(this, level.colorRes)

        val tvScore = cardRoot.findViewById<TextView>(R.id.tvRiskScore)
        val tvBadge = cardRoot.findViewById<TextView>(R.id.tvRiskStatusBadge)
        val tvRange = cardRoot.findViewById<TextView>(R.id.tvRiskRange)
        val tvMessage = cardRoot.findViewById<TextView>(R.id.tvRiskMessage)
        val progressFill = cardRoot.findViewById<View>(R.id.progressFill)
        val progressContainer = progressFill.parent as FrameLayout

        tvScore.text = score.toString()
        tvScore.setTextColor(color)

        tvBadge.text = level.label
        // backgroundTintList 사용 — 공유 Drawable 인스턴스를 직접 변조하지 않음
        tvBadge.backgroundTintList = ColorStateList.valueOf(color)

        tvRange.text = level.rangeText
        tvMessage.text = level.message

        // 프로그레스 fill (점수 비율만큼)
        progressContainer.post {
            val maxWidth = progressContainer.width
            val ratio = score.coerceIn(0, 100) / 100f
            val params = progressFill.layoutParams
            params.width = (maxWidth * ratio).toInt()
            progressFill.layoutParams = params
            progressFill.backgroundTintList = ColorStateList.valueOf(color)
        }

        // 위험 단계면 카드에 빨강 stroke, 아니면 제거
        // mutate() 로 이 뷰 전용 Drawable 복사본을 만들어 다른 bg_card 뷰에 영향 없도록 처리
        val cardBg = cardRoot.background.mutate() as? GradientDrawable
        if (cardBg != null) {
            if (level == RiskLevel.DANGER) {
                cardBg.setStroke(dp(2), color)
            } else {
                cardBg.setStroke(0, 0)
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

    /**
     * 위험 감지 바텀시트 - 그 시점의 점수와 감지 시각을 받아 표시.
     */
    private fun showFallAlertDialog(score: Int, detectedAtMillis: Long) {
        if (alertDialog?.isShowing == true) return

        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_fall_alert, null)

        // 감지 시각 텍스트
        val timeFormat = SimpleDateFormat("a hh:mm", Locale.KOREAN)
        view.findViewById<TextView>(R.id.tvDetectedTime).text =
            "감지 시각 · ${timeFormat.format(Date(detectedAtMillis))}"

        // 모달 안의 점수 카드 (스냅샷, 갱신 없음)
        val alertCard = view.findViewById<View>(R.id.alertRiskScoreCard)
        applyRiskScoreToCard(alertCard, score)

        view.findViewById<View>(R.id.btnAlertDismiss).setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.show()
        alertDialog = dialog
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}