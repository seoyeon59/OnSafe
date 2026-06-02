package com.example.on_safe.network

import com.example.on_safe.network.dto.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<LoginResponse>>

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<ApiResponse<Unit>>

    @POST("api/auth/check-id")
    suspend fun checkId(@Body request: CheckIdRequest): Response<ApiResponse<Unit>>

    @POST("api/auth/send-email-code")
    suspend fun sendEmailCode(@Body request: SendEmailCodeRequest): Response<ApiResponse<Unit>>

    @POST("api/auth/verify-email-code")
    suspend fun verifyEmailCode(@Body request: VerifyEmailCodeRequest): Response<ApiResponse<Unit>>

    @POST("api/auth/find-id")
    suspend fun findId(@Body request: FindIdRequest): Response<ApiResponse<FindIdResponse>>

    @POST("api/auth/send-reset-code")
    suspend fun sendResetCode(@Body request: SendResetCodeRequest): Response<ApiResponse<Unit>>

    @POST("api/auth/verify-reset-code")
    suspend fun verifyResetCode(@Body request: VerifyResetCodeRequest): Response<ApiResponse<Unit>>

    @POST("api/auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): Response<ApiResponse<Unit>>

    @POST("api/auth/refresh")
    suspend fun refreshToken(
        @Header("Refresh-Token") refreshToken: String
    ): Response<ApiResponse<TokenRefreshResponse>>

    @POST("api/auth/logout")
    suspend fun logout(
        @Header("Authorization") token: String
    ): Response<ApiResponse<Unit>>

    @GET("api/users/{userId}")
    suspend fun getUser(
        @Header("Authorization") token: String,
        @Path("userId") userId: String
    ): Response<ApiResponse<UserResponse>>

    @DELETE("api/users/{userId}")
    suspend fun deleteUser(
        @Header("Authorization") token: String,
        @Path("userId") userId: String
    ): Response<ApiResponse<Unit>>

    @POST("api/users/{userId}/verify-password")
    suspend fun verifyPassword(
        @Header("Authorization") token: String,
        @Path("userId") userId: String,
        @Body request: VerifyPasswordRequest
    ): Response<ApiResponse<Unit>>

    @PUT("api/users/{userId}")
    suspend fun updateUser(
        @Header("Authorization") token: String,
        @Path("userId") userId: String,
        @Body request: UserUpdateRequest
    ): Response<ApiResponse<UserResponse>>

    @GET("api/settings/notifications/{userId}")
    suspend fun getNotificationSettings(
        @Header("Authorization") token: String,
        @Path("userId") userId: String
    ): Response<ApiResponse<NotificationSettingsResponse>>

    @PUT("api/settings/notifications/{userId}")
    suspend fun updateNotificationSettings(
        @Header("Authorization") token: String,
        @Path("userId") userId: String,
        @Body request: NotificationSettingsRequest
    ): Response<ApiResponse<NotificationSettingsResponse>>
}