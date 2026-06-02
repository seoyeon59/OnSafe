package com.example.on_safe.network.dto

data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null
)

data class LoginResponse(
    val userId: String,
    val deviceId: String,
    val name: String,
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String
)

data class FindIdResponse(
    val userId: String
)

data class UserResponse(
    val userId: String,
    val name: String,
    val phone: String,
    val mail: String,
    val address: String?,
    val addressDetail: String?
)

data class NotificationSettingsResponse(
    val notificationEnabled: Boolean,
    val soundEnabled: Boolean,
    val vibrationEnabled: Boolean
)