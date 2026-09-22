package com.example.on_safe.network.dto

// GET /api/notifications/{userId} 응답 항목.
// 서버는 낙상(fall_detected/fall_escalated) 외에도 페어링·오프라인 등 모든 알림을 이 컬렉션에 저장한다.
// FallLog 와는 별개 — 여기 isRead 는 "사용자가 알림을 봤나", FallLog.isConfirmed 는 "보호자가 사고를 처리했나".
data class NotificationLogResponse(
    val notificationId: String,
    val title: String,
    val body: String,
    // 낙상 계열이면 연결된 fall_logs 문서의 id — 상세 조회·확인에 사용. 페어링 등에는 null.
    val logId: String?,
    // 낙상 계열이면 감지 점수, 아니면 null.
    val score: Float?,
    // true 면 fall 계열(fall_detected / fall_escalated).
    val fall: Boolean,
    val isRead: Boolean,
    val timestamp: String
)