package com.example.on_safe.data.repository

import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.FallLogResponse
import com.example.on_safe.ui.notification.NotificationItem
import com.example.on_safe.ui.notification.NotificationType
import com.example.on_safe.util.DisplayText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// 알림 전용 API 부재 — 사고이력과 동일한 GET /api/fall-logs/{userId} 재사용
// 읽음 상태 isConfirmed, 읽음 처리 PATCH .../confirm
class RealNotificationRepository : NotificationRepository {

    override suspend fun getNotifications(userId: String): List<NotificationItem> =
        FallLogSource.fetchLogs(userId, "알림 내역을 불러오지 못했습니다.")
            .sortedByDescending { it.timestamp }
            .map { it.toNotificationItem() }

    // 응답 코드 미확인 — 실패해도 읽음 표시 유지 후 다음 조회 때 재동기화 (깜빡임 방지)
    // 네트워크 예외는 호출부 NotificationViewModel이 흡수
    override suspend fun confirmNotification(userId: String, logId: String) {
        ApiClient.api.confirmFallLog(userId, logId)
    }

    private fun FallLogResponse.toNotificationItem(): NotificationItem {
        val type = if (FallLogSource.isFall(this)) NotificationType.FALL else NotificationType.WARNING
        return NotificationItem(
            id = logId,
            type = type,
            title = if (type == NotificationType.FALL) "낙상 위험 감지" else "주의 상태 감지",
            time = formatRelativeTime(timestamp),
            riskScore = score.toInt().coerceIn(0, 100),
            detectedAtMillis = parseTimestampMillis(timestamp),
            isUnread = !isConfirmed
        )
    }

    companion object {
        // 서버가 "yyyy-MM-dd'T'HH:mm:ss" 로컬(KST) 문자열을 내려준다는 전제
        // (RealAccidentHistoryRepository의 파싱과 동일 가정)
        private val serverFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.KOREA)
        private val timeOnlyFormat = SimpleDateFormat("a hh:mm", Locale.KOREA)

        private fun parseTimestampMillis(timestamp: String): Long =
            try {
                serverFormat.parse(timestamp)?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }

        // "오늘 · 오후 02:23" / "어제 · 오후 08:30" / "N일 전 · 오전 10:15" 형태 변환
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
}
