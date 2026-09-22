package com.example.on_safe.data.repository

import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.NotificationLogResponse
import com.example.on_safe.ui.notification.NotificationItem
import com.example.on_safe.ui.notification.NotificationType
import com.example.on_safe.util.DisplayText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// 알림 전용 API `GET /api/notifications/{userId}` 사용.
// 낙상·주의 외에 페어링·오프라인 등 서버가 발송한 모든 알림이 최신순으로 온다.
// 이전 구현은 사고이력(GET /api/fall-logs)을 재활용해 낙상 외 알림이 알림함에 뜨지 않던 문제가 있었다.
class RealNotificationRepository : NotificationRepository {

    // 백엔드 RiskLevel.DANGER_THRESHOLD(score > 75 strict) 와 동일 기준.
    // fall=true 는 확실한 낙상, false 지만 점수만 위험 이상인 경우도 낙상 취급.
    private companion object {
        private const val DANGER_THRESHOLD = 75f

        private val serverFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.KOREA)
        private val timeOnlyFormat = SimpleDateFormat("a hh:mm", Locale.KOREA)
    }

    override suspend fun getNotifications(userId: String): List<NotificationItem> {
        val response = ApiClient.api.getNotifications(userId)
        val body = response.body()
        if (response.isSuccessful && body?.success == true && body.data != null) {
            return body.data["notifications"].orEmpty().map { it.toNotificationItem() }
        }
        throw IllegalStateException(
            ApiClient.parseErrorMessage(response.errorBody(), "알림 내역을 불러오지 못했습니다.")
        )
    }

    // 알림함 read 처리. 서버 실패는 삼켜서 로컬 read 표시를 유지 — 다음 조회에서 서버 값이 우선한다.
    // 낙상 계열에 대해 사고이력(isConfirmed) 을 함께 처리할지는 정책상 분리 — 여기서는 read 만.
    override suspend fun confirmNotification(userId: String, notificationId: String) {
        ApiClient.api.markNotificationRead(userId, notificationId)
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

    // fall=true 는 무조건 FALL(사이렌), 점수만 위험이면 FALL, 점수만 있고 위험 미만이면 WARNING,
    // 그 외(점수 없음 = 페어링·오프라인 등)는 SYSTEM.
    private fun classify(r: NotificationLogResponse): NotificationType = when {
        r.fall -> NotificationType.FALL
        r.score != null && r.score > DANGER_THRESHOLD -> NotificationType.FALL
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