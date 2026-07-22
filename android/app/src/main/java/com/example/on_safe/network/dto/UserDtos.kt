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