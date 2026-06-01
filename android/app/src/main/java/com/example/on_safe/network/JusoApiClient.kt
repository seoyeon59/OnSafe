package com.example.on_safe.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object JusoApiClient {
    private const val BASE_URL = "https://business.juso.go.kr/"

    // ⚠️ 기존 ApiClient와 달리 기본 Gson 사용 (camelCase 그대로 매핑)
    val api: JusoApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(JusoApiService::class.java)
    }
}