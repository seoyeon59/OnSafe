package com.example.on_safe.network

import com.example.on_safe.network.dto.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

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

    @POST("api/auth/refresh")
    suspend fun refresh(@Header("Refresh-Token") refreshToken: String): Response<ApiResponse<TokenResponse>>

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

    @GET("api/settings/marketing/{userId}")
    suspend fun getMarketingConsent(
        @Path("userId") userId: String
    ): Response<ApiResponse<MarketingConsentResponse>>

    @PUT("api/settings/marketing/{userId}")
    suspend fun updateMarketingConsent(
        @Path("userId") userId: String,
        @Body request: MarketingConsentRequest
    ): Response<ApiResponse<MarketingConsentResponse>>

    // ===== Camera (실시간 위험 지수) =====

    @GET("api/camera/score/{userId}")
    suspend fun getRiskScore(@Path("userId") userId: String): Response<ApiResponse<RiskScoreResponse>>

    @GET("api/camera/status/{userId}")
    suspend fun getRiskStatus(@Path("userId") userId: String): Response<ApiResponse<RiskStatusResponse>>

    // ===== Fall Logs (사고 이력) =====

    @GET("api/fall-logs/{userId}")
    suspend fun getFallLogs(
        @Path("userId") userId: String,
        @Query("level") level: String? = null
    ): Response<ApiResponse<Map<String, List<FallLogResponse>>>>

    @DELETE("api/fall-logs/{userId}/{logId}")
    suspend fun deleteFallLog(
        @Path("userId") userId: String,
        @Path("logId") logId: String
    ): Response<ApiResponse<Unit>>

    // 1시간 유효한 signed URL — 재생/다운로드 시점마다 새로 발급받아 사용
    @GET("api/fall-logs/{userId}/{logId}/video")
    suspend fun getFallLogVideo(
        @Path("userId") userId: String,
        @Path("logId") logId: String
    ): Response<ApiResponse<Map<String, String>>>

    // 10분 유효한 업로드용 signed PUT URL 발급 (upload_url, content_type 키)
    @POST("api/fall-logs/{userId}/{logId}/upload-url")
    suspend fun getUploadUrl(
        @Path("userId") userId: String,
        @Path("logId") logId: String
    ): Response<ApiResponse<Map<String, String>>>

    // signed URL로 GCS 업로드 완료 후 호출 — 서버가 GCS 객체 존재를 재확인한 뒤 video_url 반영
    @PATCH("api/fall-logs/{userId}/{logId}/video-complete")
    suspend fun completeVideoUpload(
        @Path("userId") userId: String,
        @Path("logId") logId: String
    ): Response<ApiResponse<FallLogResponse>>

    // 낙상 로그 확인 처리 — 알림 목록의 읽음 처리에도 그대로 사용(isConfirmed == 읽음)
    @PATCH("api/fall-logs/{userId}/{logId}/confirm")
    suspend fun confirmFallLog(
        @Path("userId") userId: String,
        @Path("logId") logId: String
    ): Response<ApiResponse<ConfirmFallLogResponse>>
}
