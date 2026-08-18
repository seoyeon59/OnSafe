package com.example.on_safe.data.fake

import com.example.on_safe.data.repository.NotificationRepository
import com.example.on_safe.ui.notification.NotificationItem
import com.example.on_safe.ui.notification.NotificationType

// userId 없이 로그인 상태가 아닐 때를 대비한 로컬 더미 (RealNotificationRepository가 기본 구현체)
class FakeNotificationRepository : NotificationRepository {

    override suspend fun getNotifications(userId: String): List<NotificationItem> {
        // 호출 시점 기준 상대 시각 계산 (프로퍼티로 빼면 클래스 생성 시점에 고정되므로 함수 안에 유지)
        val now = System.currentTimeMillis()
        val min = 60_000L
        return listOf(
            NotificationItem("fake-1", NotificationType.FALL,    "낙상 위험 감지", "오늘 · 오후 02:23", 91, now - 3  * min, isUnread = true),
            NotificationItem("fake-2", NotificationType.WARNING, "주의 상태 감지", "오늘 · 오후 01:47", 68, now - 36 * min, isUnread = true),
            NotificationItem("fake-3", NotificationType.WARNING, "주의 상태 감지", "오늘 · 오전 11:05", 54, now - 3  * 60 * min, isUnread = true),
            NotificationItem("fake-4", NotificationType.FALL,    "낙상 위험 감지", "어제 · 오후 08:30", 88, now - 18 * 60 * min, isUnread = false),
            NotificationItem("fake-5", NotificationType.WARNING, "주의 상태 감지", "어제 · 오후 03:12", 61, now - 23 * 60 * min, isUnread = false),
            NotificationItem("fake-6", NotificationType.FALL,    "낙상 위험 감지", "어제 · 오전 09:44", 95, now - 29 * 60 * min, isUnread = false),
            NotificationItem("fake-7", NotificationType.WARNING, "주의 상태 감지", "2일 전 · 오후 06:20", 57, now - 44 * 60 * min, isUnread = false),
            NotificationItem("fake-8", NotificationType.WARNING, "주의 상태 감지", "2일 전 · 오전 10:15", 63, now - 52 * 60 * min, isUnread = false),
        )
    }

    override suspend fun confirmNotification(userId: String, logId: String) {
        // 더미 저장소라 서버에 반영할 게 없음
    }
}
