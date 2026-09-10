package com.example.on_safe.network.dto

// 프로퍼티명 = 통신 규약 (ResponseDtos.kt 상단 주석 참고)

// 피보호자가 발급받는 6자리 코드 + 만료까지 남은 초
data class PairingCodeResponse(
    val code: String,
    val expiresInSeconds: Long
)

// 보호자가 코드로 페어링 요청할 때 body
data class PairRequest(
    val code: String
)

// 페어링 성공 시 반환되는 피보호자 정보
data class WardResponse(
    val userId: String,
    val name: String
)

// GET /api/guardian/{userId}/wards 응답 — 서버가 {"wards": [...]}로 감싸서 내려줌
data class WardsWrapper(
    val wards: List<WardResponse>
)