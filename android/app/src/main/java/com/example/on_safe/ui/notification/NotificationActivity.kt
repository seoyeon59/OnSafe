package com.example.on_safe.ui.notification

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.on_safe.R
import com.example.on_safe.util.NotificationPermissionBanner
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationActivity : AppCompatActivity() {

    private lateinit var adapter: NotificationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        NotificationPermissionBanner.setup(this)

        // TODO: GET /notification/list API로 교체 (현재 더미 데이터)
        val now = System.currentTimeMillis()
        val min = 60_000L
        val sampleData = mutableListOf(
            NotificationItem(NotificationType.FALL,    "낙상 위험 감지", "오늘 · 오후 02:23", 91, now - 3  * min, isUnread = true),
            NotificationItem(NotificationType.WARNING, "주의 상태 감지", "오늘 · 오후 01:47", 68, now - 36 * min, isUnread = true),
            NotificationItem(NotificationType.WARNING, "주의 상태 감지", "오늘 · 오전 11:05", 54, now - 3  * 60 * min, isUnread = true),
            NotificationItem(NotificationType.FALL,    "낙상 위험 감지", "어제 · 오후 08:30", 88, now - 18 * 60 * min, isUnread = false),
            NotificationItem(NotificationType.WARNING, "주의 상태 감지", "어제 · 오후 03:12", 61, now - 23 * 60 * min, isUnread = false),
            NotificationItem(NotificationType.FALL,    "낙상 위험 감지", "어제 · 오전 09:44", 95, now - 29 * 60 * min, isUnread = false),
            NotificationItem(NotificationType.WARNING, "주의 상태 감지", "2일 전 · 오후 06:20", 57, now - 44 * 60 * min, isUnread = false),
            NotificationItem(NotificationType.WARNING, "주의 상태 감지", "2일 전 · 오전 10:15", 63, now - 52 * 60 * min, isUnread = false),
        )

        adapter = NotificationAdapter(sampleData) { position, item ->
            showFallAlertDialog(position, item)
        }

        findViewById<RecyclerView>(R.id.rv_notifications).apply {
            layoutManager = LinearLayoutManager(this@NotificationActivity)
            this.adapter = this@NotificationActivity.adapter
        }

        // WARNING은 화면 진입 시 일괄 읽음 처리
        adapter.markAllWarningsAsRead()
    }

    override fun onResume() {
        super.onResume()
        NotificationPermissionBanner.refresh(this)
    }

    // 화면 종료 시 미읽음 여부를 MainActivity로 전달
    override fun finish() {
        val hasUnread = adapter.hasUnreadItems()
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_HAS_UNREAD, hasUnread))
        super.finish()
    }

    companion object {
        const val EXTRA_HAS_UNREAD = "extra_has_unread"
    }

    // FALL 항목 클릭 시 위험 감지 모달 표시
    // 모달에서 119 또는 확인했습니다를 눌러야 읽음 처리
    private fun showFallAlertDialog(position: Int, item: NotificationItem) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_fall_alert, null)

        val timeFormat = SimpleDateFormat("a hh:mm", Locale.KOREAN)
        view.findViewById<TextView>(R.id.tvDetectedTime).text =
            "감지 시각 · ${timeFormat.format(Date(item.detectedAtMillis))}"

        val tvScore = view.findViewById<TextView>(R.id.tvRiskScore)
        val tvBadge = view.findViewById<TextView>(R.id.tvRiskStatusBadge)
        val tvRange = view.findViewById<TextView>(R.id.tvRiskRange)
        val tvMsg   = view.findViewById<TextView>(R.id.tvRiskMessage)
        val progressFill = view.findViewById<View>(R.id.progressFill)

        val (label, rangeText, message, colorRes) = when {
            item.riskScore > 75 -> RiskDisplay("위험", "위험 지수 76~100",
                "낙상이 의심됩니다. 즉시 확인이 필요합니다.", R.color.status_danger)
            item.riskScore > 50 -> RiskDisplay("주의", "위험 지수 51~75",
                "어르신의 움직임에 주의가 필요합니다.", R.color.status_warning)
            else -> RiskDisplay("정상", "위험 지수 0~50",
                "어르신이 안정적인 상태입니다.", R.color.status_normal)
        }
        val color = androidx.core.content.ContextCompat.getColor(this, colorRes)
        tvScore.text = item.riskScore.toString()
        tvScore.setTextColor(color)
        tvBadge.text = label
        tvBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        tvRange.text = rangeText
        tvMsg.text = message

        val progressContainer = progressFill.parent as android.widget.FrameLayout
        progressContainer.post {
            val ratio = item.riskScore.coerceIn(0, 100) / 100f
            progressFill.layoutParams = progressFill.layoutParams.also {
                it.width = (progressContainer.width * ratio).toInt()
            }
            progressFill.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        }

        val cardBg = view.findViewById<View>(R.id.alertRiskScoreCard).background?.mutate()
                as? android.graphics.drawable.GradientDrawable
        if (item.riskScore > 75) {
            cardBg?.setStroke((2 * resources.displayMetrics.density).toInt(), color)
        }

        // 119 또는 확인했습니다 → 읽음 처리 후 dismiss
        val markReadAndDismiss = {
            adapter.markAsRead(position)
            dialog.dismiss()
        }

        view.findViewById<View>(R.id.btn119Alert).setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:119")))
            markReadAndDismiss()
        }
        view.findViewById<View>(R.id.btnAlertDismiss).setOnClickListener {
            markReadAndDismiss()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private data class RiskDisplay(
        val label: String,
        val rangeText: String,
        val message: String,
        val colorRes: Int
    )
}
