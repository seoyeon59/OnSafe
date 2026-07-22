package com.example.on_safe.network.dto

data class RiskScoreResponse(
    val userId: String,
    val score: Float,
    val level: String,
    val updatedAt: String
)

data class RiskStatusResponse(
    val userId: String,
    val level: String,
    val score: Float,
    val colorCode: String
)