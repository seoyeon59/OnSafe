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
 * 부분 수정 요청 — Gson이 null 필드를 직렬화에서 제외.
 * 미변경 항목은 null 유지. 빈 문자열은 "값 삭제"로 전송되므로 주의.
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
