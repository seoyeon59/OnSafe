package com.example.on_safe.network.dto

data class FallLogResponse(
    val logId: String,
    val deviceId: String,
    val userId: String,
    val score: Float,
    val fall: Boolean,
    val isConfirmed: Boolean,
    val hasVideo: Boolean,
    // none(주의 등급 등 애초에 영상 미제공) / processing(위험 등급, post-이벤트 녹화·업로드 대기 중) /
    // ready(hasVideo=true와 동일 조건, 재생 가능) — 백엔드 FallLogResponse.videoStatus와 동일
    val videoStatus: String = "none",
    val timestamp: String
)

data class ConfirmFallLogResponse(
    val logId: String,
    val isConfirmed: Boolean
)
