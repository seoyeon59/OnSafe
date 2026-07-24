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
import com.example.on_safe.util.RiskScoreCardBinder
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.widget.TextView
import com.example.on_safe.data.fake.FakeNotificationRepository
import com.example.on_safe.data.repository.NotificationRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationActivity : AppCompatActivity() {

    private lateinit var adapter: NotificationAdapter

    // TODO: API 연동 시 Real 구현체로 교체
    private val notificationRepository: NotificationRepository = FakeNotificationRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        NotificationPermissionBanner.setup(this)

        val notifications = notificationRepository.getNotifications()
        adapter = NotificationAdapter(notifications) { position, item ->
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

        // 점수 카드 바인딩 (색상·배지·메시지·프로그레스·stroke 일괄 처리)
        RiskScoreCardBinder.bind(view.findViewById(R.id.alertRiskScoreCard), item.riskScore)

        // 119 또는 확인했습니다 → 읽음 처리 후 dismiss
        // TODO: 현재 position을 캡처하여 읽음 처리하므로, 실시간 알림 API 연동 후 목록이
        //       동적으로 변경될 경우 position이 실제 항목과 어긋날 수 있습니다.
        //       API 연동 시에는 position 대신 item.id(서버 고유 식별자)를 기준으로
        //       읽음 처리하도록 adapter.markAsReadById(item.id) 형태로 수정해주세요.
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
        dialog.setOnShowListener {
            // Material BottomSheetDialog 기본 둥근 배경 제거 → XML drawable 곡률만 표시
            (view.parent as? android.view.View)?.background = null
        }
        dialog.show()
    }

}
