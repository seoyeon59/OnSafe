package com.example.on_safe.data.fake

import com.example.on_safe.data.repository.NotificationRepository
import com.example.on_safe.ui.notification.NotificationItem
import com.example.on_safe.ui.notification.NotificationType

// TODO: GET /notification/list API 연동 후 RealNotificationRepository로 교체
class FakeNotificationRepository : NotificationRepository {

    override fun getNotifications(): MutableList<NotificationItem> {
        // 호출 시점 기준 상대 시각 계산 (프로퍼티로 빼면 클래스 생성 시점에 고정되므로 함수 안에 유지)
        val now = System.currentTimeMillis()
        val min = 60_000L
        return mutableListOf(
            NotificationItem(NotificationType.FALL,    "낙상 위험 감지", "오늘 · 오후 02:23", 91, now - 3  * min, isUnread = true),
            NotificationItem(NotificationType.WARNING, "주의 상태 감지", "오늘 · 오후 01:47", 68, now - 36 * min, isUnread = true),
            NotificationItem(NotificationType.WARNING, "주의 상태 감지", "오늘 · 오전 11:05", 54, now - 3  * 60 * min, isUnread = true),
            NotificationItem(NotificationType.FALL,    "낙상 위험 감지", "어제 · 오후 08:30", 88, now - 18 * 60 * min, isUnread = false),
            NotificationItem(NotificationType.WARNING, "주의 상태 감지", "어제 · 오후 03:12", 61, now - 23 * 60 * min, isUnread = false),
            NotificationItem(NotificationType.FALL,    "낙상 위험 감지", "어제 · 오전 09:44", 95, now - 29 * 60 * min, isUnread = false),
            NotificationItem(NotificationType.WARNING, "주의 상태 감지", "2일 전 · 오후 06:20", 57, now - 44 * 60 * min, isUnread = false),
            NotificationItem(NotificationType.WARNING, "주의 상태 감지", "2일 전 · 오전 10:15", 63, now - 52 * 60 * min, isUnread = false),
        )
    }
}