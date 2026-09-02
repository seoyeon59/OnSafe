package com.example.on_safe.network

import com.example.on_safe.network.dto.DeviceListResponse
import com.example.on_safe.network.dto.DeviceRegisterRequest
import com.example.on_safe.network.dto.DeviceRegisterResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Python AI 서버(8000) 전용 API.
 * 인증은 Kotlin 서버와 동일한 JWT, 응답만 flat JSON이라 ApiService와 분리.
 */
interface AiApiService {

    // 계정에 등록된 카메라 기기 목록 — 보호자 홈 기기 ID 표시용
    @GET("api/devices/{userId}")
    suspend fun getDevices(@Path("userId") userId: String): Response<DeviceListResponse>

    // upsert — 기등록 기기는 소유권만 갱신, 중복 생성 없음
    @POST("api/devices/{userId}")
    suspend fun registerDevice(
        @Path("userId") userId: String,
        @Body request: DeviceRegisterRequest
    ): Response<DeviceRegisterResponse>
}
