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
    val marketingConsent: Boolean = false,
    // verifyEmailCode 응답에서 받은 1회용 티켓. 서버가 이 티켓과 함께 온 register 요청만 통과시켜,
    // 인증만 마친 다른 사용자의 이메일을 도용해 자기 계정에 붙이는 이메일 선점(squatting) 시나리오를 차단한다.
    val emailVerifyTicket: String
)

data class CheckIdRequest(
    val userId: String
)

data class CheckMailRequest(
    val mail: String
)

data class SendEmailCodeRequest(
    val mail: String
)

data class VerifyEmailCodeRequest(
    val mail: String,
    val code: String
)

data class FindIdRequest(
    val name: String,
    val mail: String
)

data class SendResetCodeRequest(
    val userId: String,
    val mail: String
)

data class VerifyResetCodeRequest(
    val userId: String,
    val code: String
)

data class ResetPasswordRequest(
    val userId: String,
    val newPassword: String
)

// FCM 푸시 토큰 등록/해제 요청.
// 서버는 이 토큰으로 승인·거부·오프라인 등 실시간 이벤트를 발송한다.
// deviceId 를 함께 보내 같은 계정의 여러 기기를 구분 — LoginRequest 와 동일한 ANDROID_ID.
data class FcmTokenRequest(
    val fcmToken: String,
    val deviceId: String
)
