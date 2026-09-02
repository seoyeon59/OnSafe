package com.example.on_safe.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * 도로명주소 API(외부 도메인) 전용 클라이언트.
 *
 * ApiClient와 반드시 분리 유지:
 * - ApiClient의 authInterceptor는 경로만 보고 Bearer를 붙이므로, 공유하면 사용자 JWT가
 *   business.juso.go.kr로 새어 나간다.
 * - 응답 키가 camelCase라 ApiClient의 snake_case 필드명 정책도 맞지 않는다.
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
