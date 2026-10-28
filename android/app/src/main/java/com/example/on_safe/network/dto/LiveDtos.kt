package com.example.on_safe.network.dto

/**
 * `POST /api/live/{elder_user_id}/session` 응답 — LiveKit 방 접속 정보(시청 전용 토큰).
 * 같은 POST가 시작·연장을 겸한다. [requestDelivered]는 새 세션일 때만 채워지고 연장이면 null.
 * [expiresAt]은 서버 LocalDateTime 문자열("yyyy-MM-ddTHH:mm:ss…") — 연장 시점 계산에 쓴다.
 */
data class LiveSessionResponse(
    val serverUrl: String,
    val room: String,
    val token: String,
    val expiresAt: String?,
    val requestDelivered: Boolean?
)
