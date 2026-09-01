package com.example.on_safe.data.repository

import com.example.on_safe.ui.notification.NotificationItem

interface NotificationRepository {
    suspend fun getNotifications(userId: String): List<NotificationItem>

    // 알림(=낙상 로그) 읽음 처리 — 서버 저장 필드는 isConfirmed
    suspend fun confirmNotification(userId: String, logId: String)
}
