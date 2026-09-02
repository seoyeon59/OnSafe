package com.example.on_safe.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * 도로명주소 API(외부 도메인) 전용 — ApiClient와 분리 유지 필수.
 * 공유 시 authInterceptor가 경로만 보고 Bearer를 붙여 사용자 JWT 외부 유출.
 * 응답 키도 camelCase라 snake_case 정책 불일치.
 */
object JusoApiClient {

    private const val BASE_URL = "https://business.juso.go.kr/"

    val api: JusoApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(JusoApiService::class.java)
    }
}
