package com.example.on_safe.network.dto

// 프로퍼티명 = 통신 규약 (ResponseDtos.kt 상단 주석 참고)

data class LoginRequest(
    val userId: String,
    val password: String,
    val deviceId: String
)

data class RegisterRequest(
    val userId: String,
    val password: String,
    val name: String,
    val mail: String,
    val phone: String,
    val address: String? = null,
    val addressDetail: String? = null,
    // 필수 동의 3종 — 서버가 동의 이력(consents)을 남기므로 실제 체크값을 그대로 보낸다.
    // Step1에서 셋 다 체크해야 다음으로 넘어가지만, 값을 고정하면 이력의 의미가 없어진다.
    val termsAgreed: Boolean,
    val privacyPolicyAgreed: Boolean,
    val sensitiveInfoAgreed: Boolean,
    // 서버(users.marketing_consent)가 동의·철회 시점을 함께 기록하기 위한 신호.
    // Step1 동의 화면의 선택 항목 체크값이 Step2를 거쳐 그대로 전달된다.
    val marketingConsent: Boolean = false
)

data class CheckIdRequest(
    val userId: String
)

data class CheckMailRequest(
    val mail: String
)

data class FindIdRequest(
    val name: String,
    val mail: String,
    // verifyEmailCode 응답 티켓 — 1회용, 요청 시 소비
    val emailVerifyTicket: String
)

// 비밀번호 찾기 본인확인 — 세 값이 모두 일치해야 서버가 재설정 티켓을 발급한다
data class VerifyResetIdentityRequest(
    val userId: String,
    val name: String,
    val mail: String
)

// userId만으로 바꿀 수 있으면 아이디만 알아도 남의 비밀번호를 바꿀 수 있다.
// 본인확인에서 받은 티켓을 함께 보내 확인을 통과한 요청만 받게 한다.
data class ResetPasswordRequest(
    val userId: String,
    // verifyResetCode 응답 티켓 — 1회용·10분
    val resetTicket: String,
    val newPassword: String
)

// FCM 푸시 토큰 등록/해제 요청.
// 서버는 이 토큰으로 승인·거부·오프라인 등 실시간 이벤트를 발송한다.
// deviceId 를 함께 보내 같은 계정의 여러 기기를 구분 — LoginRequest 와 동일한 ANDROID_ID.
data class FcmTokenRequest(
    val fcmToken: String,
    val deviceId: String
)

// 로그아웃 본문 — 서버가 세션 종료와 함께 이 기기 FCM 토큰 해제
data class LogoutRequest(
    val fcmToken: String?,
    val deviceId: String?
)

// 탈퇴 본문 — verifyPassword 응답의 재인증 티켓
data class DeleteUserRequest(
    val reauthTicket: String
)
