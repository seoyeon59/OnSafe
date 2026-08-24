package com.example.on_safe.network.dto

data class RiskScoreResponse(
    val userId: String,
    val score: Float,
    val level: String,
    // 서버가 내려주지 않는 경우를 대비해 nullable — 촬영 종료 감지에 쓰인다
    val updatedAt: String?
)

data class RiskStatusResponse(
    val userId: String,
    val level: String,
    val score: Float,
    val colorCode: String
)