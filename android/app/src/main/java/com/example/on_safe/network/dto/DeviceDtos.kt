package com.example.on_safe.network.dto

// Python AI 서버 전용 DTO — {success, message, data} 래퍼 없는 flat JSON
// Gson 필드명 정책(LOWER_CASE_WITH_UNDERSCORES)으로 deviceId->device_id 자동 변환

data class DeviceListResponse(
    val devices: List<DeviceItem> = emptyList()
)

// status·lastSeen은 서버 미갱신이라 현재 미사용 — 화면 상태는 위험 지수 폴링으로 판정
data class DeviceItem(
    val deviceId: String? = null,
    val deviceName: String? = null,
    val status: String? = null,
    val lastSeen: String? = null
)

data class DeviceRegisterRequest(
    val deviceId: String,
    val deviceName: String,
    val cameraUrl: String? = null
)

data class DeviceRegisterResponse(
    val status: String? = null,
    val deviceId: String? = null
)
