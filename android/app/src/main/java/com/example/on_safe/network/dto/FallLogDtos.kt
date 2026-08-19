package com.example.on_safe.network.dto

data class FallLogResponse(
    val logId: String,
    val deviceId: String,
    val userId: String,
    val score: Float,
    val fall: Boolean,
    val isConfirmed: Boolean,
    val hasVideo: Boolean,
    val timestamp: String
)

data class ConfirmFallLogResponse(
    val logId: String,
    val isConfirmed: Boolean
)
