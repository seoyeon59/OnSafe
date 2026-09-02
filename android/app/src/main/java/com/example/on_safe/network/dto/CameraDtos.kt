package com.example.on_safe.network.dto

data class RiskScoreResponse(
    val userId: String,
    val score: Float,
    // "정상" / "주의" / "위험" / "오류"(추론 실패)
    // Gson이 생성자를 건너뛰어 응답 누락 시 non-null 선언에도 null 유입 — nullable 유지
    val level: String?,
    // AI 추론 성공 시각 — 서버 미제공 대비 nullable
    val updatedAt: String?,
    // 원시 프레임 도착 시각(추론 결과와 무관) — 하트비트 미지원 구버전 서버 대비 nullable.
    // updatedAt과 분리해야 "프레임 끊김"과 "추론만 실패"를 구분 가능
    val deviceSeenAt: String? = null
)

data class RiskStatusResponse(
    val userId: String,
    val level: String,
    val score: Float,
    val colorCode: String
)
