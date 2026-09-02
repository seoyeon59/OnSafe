package com.example.on_safe.network.dto

data class FallLogResponse(
    val logId: String,
    val deviceId: String,
    val userId: String,
    val score: Float,
    val fall: Boolean,
    val isConfirmed: Boolean,
    val hasVideo: Boolean,
    /**
     * none(영상 미제공) / processing(위험 등급, 업로드 대기) / ready(재생 가능).
     * Gson이 생성자를 건너뛰어 기본값 미적용 — video_status 누락 시 null 유입.
     * nullable 유지 후 매핑 시점에 대체.
     */
    val videoStatus: String?,
    val timestamp: String
)

data class ConfirmFallLogResponse(
    val logId: String,
    val isConfirmed: Boolean
)
