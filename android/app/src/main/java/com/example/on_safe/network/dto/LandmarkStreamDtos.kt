package com.example.on_safe.network.dto

// WS /ws/stream 클라이언트 → 서버 메시지 (v4.0_onsafe_api_spec.md 참고)
// Gson 필드명 정책(LOWER_CASE_WITH_UNDERSCORES)으로 userId->user_id, deviceId->device_id 자동 변환

data class InitMessage(
    val type: String = "init",
    val userId: String,
    val deviceId: String
)

data class LandmarkPoint(
    val x: Float,
    val y: Float,
    val z: Float,
    val v: Float
)

data class FrameMessage(
    val type: String = "frame",
    val frame: Int,
    val timestamp: Float,
    val landmarks: List<LandmarkPoint>
)

// 서버 → 클라이언트 메시지는 type으로 분기해서 파싱하므로 공용 필드만 모아둠
data class StreamServerMessage(
    val type: String,
    val fallScore: Float? = null,
    val fall: Boolean? = null,
    val level: String? = null,
    // 이 프레임에서 fall_log가 새로 저장된 경우에만 존재 (그 외 null) — v4.0 스펙 WS 섹션 참고
    val logId: String? = null
)
