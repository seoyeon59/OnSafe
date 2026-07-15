package com.example.on_safe.network

import android.util.Log
import com.example.on_safe.BuildConfig
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    // BASE_URL을 BuildConfig로 뺐음 — build.gradle.kts에서 debug/release 자동 분기
    private val BASE_URL = BuildConfig.BASE_URL

    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    private val httpClient = OkHttpClient.Builder()
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