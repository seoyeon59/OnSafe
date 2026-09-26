package com.example.on_safe.data.repository

import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.errorMessage
import com.example.on_safe.network.isOk
import com.example.on_safe.network.dto.NotificationLogResponse
import com.example.on_safe.ui.notification.NotificationItem
import com.example.on_safe.ui.notification.NotificationType
import com.example.on_safe.util.DisplayText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// 알림 전용 API `GET /api/notifications/{userId}` — 낙상·주의·페어링·오프라인 전체 알림, 최신순
class RealNotificationRepository : NotificationRepository {

    private companion object {
        private val serverFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.KOREA)
        private val timeOnlyFormat = SimpleDateFormat("a hh:mm", Locale.KOREA)
    }

    override suspend fun getNotifications(userId: String): List<NotificationItem> {
        val response = ApiClient.api.getNotifications(userId)
        val data = response.body()?.data
        if (response.isOk && data != null) {
            return data["notifications"].orEmpty().map { it.toNotificationItem() }
        }
        throw IllegalStateException(response.errorMessage("알림 내역을 불러오지 못했습니다."))
    }

    // 알림 읽음 처리. 응답 코드 미확인 — 다음 조회 때 서버 값으로 재동기화
    override suspend fun markRead(userId: String, notificationId: String) {
        ApiClient.api.markNotificationRead(userId, notificationId)
    }

    // 낙상 모달의 명시 확인 — 사고 처리(isConfirmed)로 15분 재알림 중단
    override suspend fun confirmFall(userId: String, logId: String) {
        ApiClient.api.confirmFallLog(userId, logId)
    }

    private fun NotificationLogResponse.toNotificationItem(): NotificationItem {
        val type = classify(this)
        return NotificationItem(
            id = notificationId,
            logId = logId,
            type = type,
            title = title,
            body = body,
            time = formatRelativeTime(timestamp),
            riskScore = score?.toInt()?.coerceIn(0, 100) ?: 0,
            detectedAtMillis = parseTimestampMillis(timestamp),
            isUnread = !isRead
        )
    }

    // 점수 없음 = 페어링·오프라인 등 시스템 알림
    private fun classify(r: NotificationLogResponse): NotificationType = when {
        r.fall -> NotificationType.FALL
        r.score != null && r.score > FallLogSource.DANGER_THRESHOLD -> NotificationType.FALL
        r.score != null -> NotificationType.WARNING
        else -> NotificationType.SYSTEM
    }

    private fun parseTimestampMillis(timestamp: String): Long =
        try {
            serverFormat.parse(timestamp)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }

    // "오늘 · 오후 02:23" / "어제 · 오후 08:30" / "N일 전 · 오전 10:15"
    private fun formatRelativeTime(timestamp: String): String {
        // 파싱 실패 시 서버 원문 노출 방지
        val date = try { serverFormat.parse(timestamp) } catch (e: Exception) { null }
            ?: return DisplayText.NO_TIME
        val dayDiff = daysBetween(date, Date())
        val dayLabel = when {
            dayDiff <= 0 -> "오늘"
            dayDiff == 1 -> "어제"
            else -> "${dayDiff}일 전"
        }
        return "$dayLabel · ${timeOnlyFormat.format(date)}"
    }

    private fun daysBetween(from: Date, to: Date): Int {
        fun startOfDay(d: Date) = Calendar.getInstance().apply {
            time = d
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val diffMs = startOfDay(to) - startOfDay(from)
        return (diffMs / (24 * 60 * 60 * 1000)).toInt()
    }
}