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

// 피보호자가 연결 요청을 승인할 때 body — 승인 창의 "실시간 영상 보기 허용" 선택 값(live_video_enabled).
// null이면 Gson이 필드를 빼고 보내({}) 서버가 기존 동의를 유지한다 — 재페어링 시 이전 동의를 이어 가는 경로.
// 승인 창은 현재 동의를 체크박스에 그대로 보여 주고, 바꿨을 때만 값을 채운다
data class ApprovePairingRequest(
    val liveVideoEnabled: Boolean?
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

// GET /api/guardian/{userId}/pair/{counterpartUserId}/unpair-check — 해제 전 확인.
// 피보호자의 미확인 위험 이벤트 건수(최근 48시간·연결 이후, 재알림 대상과 같은 기준)
data class UnpairCheckResponse(
    val unconfirmedDangerCount: Int
)