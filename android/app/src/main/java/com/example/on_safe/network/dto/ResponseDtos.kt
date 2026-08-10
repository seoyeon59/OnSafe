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

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String
)