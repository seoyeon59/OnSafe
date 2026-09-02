package com.example.on_safe.network.dto

data class RiskScoreResponse(
    val userId: String,
    val score: Float,
    val level: String,
    // 촬영 종료 감지 기준값 — 서버 미제공 대비 nullable
    val updatedAt: String?
)

data class RiskStatusResponse(
    val userId: String,
    val level: String,
    val score: Float,
    val colorCode: String
)
