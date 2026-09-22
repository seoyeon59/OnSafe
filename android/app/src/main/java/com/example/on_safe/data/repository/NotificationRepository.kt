package com.example.on_safe.data.repository

import com.example.on_safe.ui.notification.NotificationItem

interface NotificationRepository {
    suspend fun getNotifications(userId: String): List<NotificationItem>

    // 알림 읽음 처리 — 파라미터명은 notificationId(알림함 API의 서버 id).
    // 낙상 사고 자체의 처리(FallLog.isConfirmed)는 별개로 사고이력 화면에서 관리한다.
    suspend fun confirmNotification(userId: String, notificationId: String)
}
