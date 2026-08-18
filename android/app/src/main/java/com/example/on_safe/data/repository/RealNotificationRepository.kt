package com.example.on_safe.data.repository

import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.FallLogResponse
import com.example.on_safe.ui.notification.NotificationItem
import com.example.on_safe.ui.notification.NotificationType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// 알림 목록 전용 API가 따로 없어(백엔드 NotificationController 없음), 사고이력과 같은
// GET /api/fall-logs/{userId}를 재사용한다. 읽음 상태는 fall-logs의 isConfirmed를 그대로 쓰고,
// 읽음 처리는 PATCH .../confirm 호출로 반영한다.
class RealNotificationRepository : NotificationRepository {

    override suspend fun getNotifications(userId: String): List<NotificationItem> {
        val response = ApiClient.api.getFallLogs(userId)
        val body = response.body()
        if (response.isSuccessful && body?.success == true && body.data != null) {
            return body.data["logs"].orEmpty()
                .sortedByDescending { it.timestamp }
                .map { it.toNotificationItem() }
        }
        throw IllegalStateException(
            ApiClient.parseErrorMessage(response.errorBody(), "알림 내역을 불러오지 못했습니다.")
        )
    }

    override suspend fun confirmNotification(userId: String, logId: String) {
        ApiClient.api.confirmFallLog(userId, logId)
        // 실패해도 예외를 던지지 않음 — 화면에는 이미 읽음으로 표시된 상태를 유지하고,
        // 다음 목록 조회 때 서버의 isConfirmed 값으로 다시 맞춰진다.
    }

    // 백엔드 RiskLevel.DANGER_THRESHOLD(score>75 strict)와 동일 기준 — AccidentHistory와 통일
    private fun FallLogResponse.toNotificationItem(): NotificationItem {
        val type = if (fall || score > 75f) NotificationType.FALL else NotificationType.WARNING
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
        // 서버가 "yyyy-MM-dd'T'HH:mm:ss" 형태의 로컬(KST) 시각 문자열을 내려준다는 전제
        // (AccidentHistory 쪽 timestamp.substring(11,16) 파싱과 동일 가정)
        private val serverFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.KOREA)
        private val timeOnlyFormat = SimpleDateFormat("a hh:mm", Locale.KOREA)

        private fun parseTimestampMillis(timestamp: String): Long =
            try {
                serverFormat.parse(timestamp)?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }

        // "오늘 · 오후 02:23" / "어제 · 오후 08:30" / "N일 전 · 오전 10:15" 형태로 변환
        private fun formatRelativeTime(timestamp: String): String {
            val date = try { serverFormat.parse(timestamp) } catch (e: Exception) { null } ?: return timestamp
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
