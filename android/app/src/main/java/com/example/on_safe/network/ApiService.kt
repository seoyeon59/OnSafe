package com.example.on_safe.network

import com.example.on_safe.network.dto.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface ApiService {

    // ===== Auth =====

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

    @POST("api/auth/logout")
    suspend fun logout(): Response<ApiResponse<Unit>>

    // ===== User =====

    @GET("api/users/{userId}")
    suspend fun getUser(@Path("userId") userId: String): Response<ApiResponse<UserResponse>>

    @PUT("api/users/{userId}")
    suspend fun updateUser(
        @Path("userId") userId: String,
        @Body request: UserUpdateRequest
    ): Response<ApiResponse<UserResponse>>

    @POST("api/users/{userId}/verify-password")
    suspend fun verifyPassword(
        @Path("userId") userId: String,
        @Body request: VerifyPasswordRequest
    ): Response<ApiResponse<Unit>>

    @DELETE("api/users/{userId}")
    suspend fun deleteUser(@Path("userId") userId: String): Response<ApiResponse<Unit>>

    // ===== Settings =====

    @GET("api/settings/notifications/{userId}")
    suspend fun getNotificationSettings(
        @Path("userId") userId: String
    ): Response<ApiResponse<NotificationSettingsResponse>>

    @PUT("api/settings/notifications/{userId}")
    suspend fun updateNotificationSettings(
        @Path("userId") userId: String,
        @Body request: NotificationSettingsRequest
    ): Response<ApiResponse<NotificationSettingsResponse>>

    // ===== Camera (실시간 위험 지수) =====

    @GET("api/camera/score/{userId}")
    suspend fun getRiskScore(@Path("userId") userId: String): Response<ApiResponse<RiskScoreResponse>>

    @GET("api/camera/status/{userId}")
    suspend fun getRiskStatus(@Path("userId") userId: String): Response<ApiResponse<RiskStatusResponse>>
}