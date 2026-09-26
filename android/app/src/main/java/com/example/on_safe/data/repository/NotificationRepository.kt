package com.example.on_safe.data.repository

import com.example.on_safe.ui.notification.NotificationItem

interface NotificationRepository {
    suspend fun getNotifications(userId: String): List<NotificationItem>

    // 알림 읽음(isRead)
    suspend fun markRead(userId: String, notificationId: String)

    // 낙상 사고 처리(FallLog.isConfirmed) — 읽음과 별개 축
    suspend fun confirmFall(userId: String, logId: String)
}
