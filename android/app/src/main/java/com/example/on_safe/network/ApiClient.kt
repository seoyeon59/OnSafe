package com.example.on_safe.network

import android.util.Log
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    // 에뮬레이터: 10.0.2.2 | 실기기: 실제 서버 IP로 변경
    private const val BASE_URL = "http://10.0.2.2:8080/"

    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    private val httpClient = OkHttpClient.Builder()
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
