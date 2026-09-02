package com.example.on_safe.network.dto

/*
 * 프로퍼티명 = 통신 규약.
 * ApiClient의 Gson이 LOWER_CASE_WITH_UNDERSCORES를 적용하므로 accessToken → access_token으로 나간다.
 * 이름을 바꾸면 컴파일은 통과하고 런타임에만 깨지므로 서버 스펙과 함께 변경할 것.
 *
 * 또한 Gson은 Unsafe로 인스턴스를 만들어 생성자·기본값을 건너뛴다.
 * 응답에 없는 필드는 타입이 non-null이어도 실제로는 null이 들어오므로,
 * 서버가 항상 보낸다고 보장되지 않는 값은 nullable로 선언한다.
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
