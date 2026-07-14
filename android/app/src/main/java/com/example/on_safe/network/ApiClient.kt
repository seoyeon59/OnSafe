package com.example.on_safe.network

import android.content.Context
import android.util.Log
import com.example.on_safe.util.TokenManager
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    // 에뮬레이터: 10.0.2.2 | 실기기: 실제 서버 IP로 변경
    private const val BASE_URL = "http://10.0.2.2:8080/"

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    // 인증이 필요한 요청에 자동으로 Bearer 토큰 첨부
    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val token = if (::appContext.isInitialized) TokenManager.getAccessToken(appContext) else null
        val request = if (!token.isNullOrBlank()) {
            original.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }
        chain.proceed(request)
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(HttpLoggingInterceptor { message -> Log.d("OkHttp", message) }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }

    fun parseErrorMessage(errorBody: okhttp3.ResponseBody?, fallback: String): String {
        return try {
            val json = errorBody?.string() ?: return fallback
            gson.fromJson(json, com.example.on_safe.network.dto.ApiResponse::class.java)?.message ?: fallback
        } catch (e: Exception) {
            fallback
        }
    }
}