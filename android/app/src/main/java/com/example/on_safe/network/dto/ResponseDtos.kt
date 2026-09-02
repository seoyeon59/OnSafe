package com.example.on_safe.network.dto

/*
 * 프로퍼티명 = 통신 규약 — Gson이 accessToken → access_token 변환.
 * 이름 변경 시 런타임에만 깨지므로 서버 스펙과 동시 변경 필수.
 * Gson은 생성자·기본값을 건너뛰므로, 서버 보장이 없는 값은 nullable 선언.
 */

data class ApiResponse<T>(
    val success: Boolean,
    // 성공 응답에서 생략될 수 있어 nullable — 호출부는 ApiResult.errorMessage()로 대체 문구 확보
    val message: String?,
    val data: T? = null
)

data class LoginResponse(
    val userId: String,
    val deviceId: String,
    val name: String,
    val accessToken: String,
    val refreshToken: String,
    // 미사용 — "Bearer" 고정값이라 헤더 조립은 ApiClient가 직접 처리. 서버 스펙 명시 목적으로 유지
    val tokenType: String
)

data class FindIdResponse(
    val userId: String
)

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String
)
