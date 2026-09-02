package com.example.on_safe.network.dto

data class UserResponse(
    val userId: String,
    val name: String,
    val mail: String,
    val phone: String,
    val address: String?,
    val addressDetail: String?,
    val createdAt: String
)

/**
 * 부분 수정 요청.
 * Gson 기본 설정은 null 필드를 직렬화에서 제외하므로, 바꾸지 않을 항목은 null로 두면
 * JSON에 아예 실리지 않는다. 빈 문자열을 넣으면 "값을 지움"으로 전송되니 주의.
 */
data class UserUpdateRequest(
    val name: String? = null,
    val currentPassword: String? = null,
    val password: String? = null,
    val mail: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val addressDetail: String? = null
)

data class VerifyPasswordRequest(
    val currentPassword: String
)
