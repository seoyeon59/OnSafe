package com.example.on_safe.network

import android.content.Context
import android.util.Log
import com.example.on_safe.util.TokenManager
import com.example.on_safe.BuildConfig
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    // BASE_URL을 BuildConfig로 뺐음 — build.gradle.kts에서 debug/release 자동 분기
    private val BASE_URL = BuildConfig.BASE_URL

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
        .apply {
            // 로깅은 디버그 빌드에서만 — 릴리즈에서 토큰 등 민감 정보가 logcat에 노출되지 않도록
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor { message -> Log.d("OkHttp", message) }.apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
            }
        }
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