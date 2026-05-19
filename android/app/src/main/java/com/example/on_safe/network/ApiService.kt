package com.example.on_safe.network

import com.example.on_safe.network.dto.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

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
}
