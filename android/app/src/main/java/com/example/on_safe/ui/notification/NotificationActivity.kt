package com.example.on_safe.ui.notification

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.on_safe.R
import com.example.on_safe.ui.notification.NotificationAdapter
import com.example.on_safe.ui.notification.NotificationItem
import com.example.on_safe.ui.notification.NotificationType

class NotificationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification)

        // 뒤로가기 버튼
        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // 하드코딩 샘플 데이터 (Figma 기준)
        val sampleData = mutableListOf(
            NotificationItem(NotificationType.FALL,    "낙상 위험 감지", "오늘 · 오후 02:23", 88, isUnread = true),
            NotificationItem(NotificationType.WARNING, "주의 상태 감지", "오늘 · 오후 02:23", 61, isUnread = true),
            NotificationItem(NotificationType.WARNING, "주의 상태 감지", "오늘 · 오후 02:23", 61, isUnread = true),
            NotificationItem(NotificationType.FALL,    "낙상 위험 감지", "오늘 · 오후 02:23", 88, isUnread = false),
            NotificationItem(NotificationType.WARNING, "주의 상태 감지", "오늘 · 오후 02:23", 61, isUnread = false),
            NotificationItem(NotificationType.WARNING, "주의 상태 감지", "오늘 · 오후 02:23", 61, isUnread = false),
            NotificationItem(NotificationType.WARNING, "주의 상태 감지", "오늘 · 오후 02:23", 61, isUnread = false),
            NotificationItem(NotificationType.WARNING, "주의 상태 감지", "오늘 · 오후 02:23", 61, isUnread = false),
        )

        val adapter = NotificationAdapter(sampleData) { item ->
            // TODO: 낙상 모달 띄우기
            android.widget.Toast.makeText(this, "위험 지수 ${item.riskScore}", android.widget.Toast.LENGTH_SHORT).show()
        }

        findViewById<RecyclerView>(R.id.rv_notifications).apply {
            layoutManager = LinearLayoutManager(this@NotificationActivity)
            this.adapter = adapter
        }
    }
}