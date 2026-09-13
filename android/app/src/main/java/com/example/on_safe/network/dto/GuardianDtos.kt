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

// pair() 응답 — 즉시 성립이 아니라 피보호자 승인 대기 상태. request_id 는 승인/거부 엔드포인트에
// 넘길 식별자, expires_in_seconds 는 요청이 자동 소멸되기까지 남은 시간(백엔드 기본 1800초=30분).
data class PairingRequestResponse(
    val requestId: String,
    val expiresInSeconds: Long
)

// 페어링 성공 시 반환되는 피보호자 정보 (approve 응답에도 사용)
data class WardResponse(
    val userId: String,
    val name: String
)

// GET /api/guardian/{userId}/wards 응답 — 서버가 {"wards": [...]}로 감싸서 내려줌
data class WardsWrapper(
    val wards: List<WardResponse>
)

// GET /api/guardian/{userId}/my-guardian — 1:1 정책상 최대 한 명, 없으면 null
data class GuardianResponse(
    val userId: String,
    val name: String
)