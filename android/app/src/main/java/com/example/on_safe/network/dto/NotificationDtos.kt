package com.example.on_safe.network.dto

// GET /api/notifications/{userId} 응답 항목.
// isRead = 알림 확인 여부, FallLog.isConfirmed = 사고 처리 여부 (별개 축)
data class NotificationLogResponse(
    val notificationId: String,
    val title: String,
    val body: String,
    // 낙상 계열의 fall_logs id. 그 외 null
    val logId: String?,
    // 낙상·주의 감지 점수. 그 외 null
    val score: Float?,
    // fall_detected / fall_escalated 여부
    val fall: Boolean,
    val isRead: Boolean,
    val timestamp: String,
    // 알림 종류 — 푸시 data의 event와 같은 코드(fall_detected·pairing_unpaired 등). 필드 추가 전 저장분은 null
    val event: String?,
    // 이 알림의 대상 피보호자 — 연결이 바뀐 뒤 이전 피보호자 알림 구분용. 필드 추가 전 저장분은 null
    val elderUserId: String?
)