package com.example.on_safe.network.dto

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
    // 서버(users.marketing_consent)와 동의 시점/철회 시점을 함께 기록하기 위한 신호.
    // 회원가입 UI에 체크박스가 붙기 전까지는 기본값 false로 전송.
    val marketingConsent: Boolean = false
)

data class CheckIdRequest(
    val userId: String
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