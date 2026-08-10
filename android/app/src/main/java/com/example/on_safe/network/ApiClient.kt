package com.example.on_safe.network

import android.content.Context
import android.util.Log
import com.example.on_safe.util.TokenManager
import com.example.on_safe.BuildConfig
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Route
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

    // 인증 불필요 엔드포인트 — 저장된 토큰이 만료됐거나 잘못된 상태에서
    // 로그인/회원가입 등 공개 API에도 헤더가 붙으면 서버가 401을 던져 로그인 자체가 막힘
    private val publicEndpoints = setOf(
        "api/auth/login",
        "api/auth/register",
        "api/auth/check-id",
        "api/auth/send-email-code",
        "api/auth/verify-email-code",
        "api/auth/find-id",
        "api/auth/send-reset-code",
        "api/auth/verify-reset-code",
        "api/auth/reset-password",
        // refresh는 Refresh-Token 헤더로 인증하므로 만료된 Bearer 첨부 방지
        "api/auth/refresh"
    )

    // 인증이 필요한 요청에만 자동으로 Bearer 토큰 첨부
    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val path = original.url.encodedPath.trimStart('/')
        if (path in publicEndpoints) {
            return@Interceptor chain.proceed(original)
        }
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

    // 401 응답 시 refresh 토큰으로 access 토큰을 재발급받아 요청을 자동 재시도.
    // 동시성: 여러 요청이 동시에 401을 받아도 refresh는 한 번만 수행되도록 락으로 보호.
    private val refreshLock = Any()

    private val tokenAuthenticator = Authenticator { _: Route?, response: Response ->
        // 이미 한 번 재시도했다면 그만 (Authenticator 무한 루프 방지)
        if (responseCount(response) >= 2) return@Authenticator null
        if (!::appContext.isInitialized) return@Authenticator null

        synchronized(refreshLock) {
            val requestAccessToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            val currentAccessToken = TokenManager.getAccessToken(appContext)

            // 다른 스레드가 이미 갱신했다면 새 토큰으로 재시도만
            if (!currentAccessToken.isNullOrBlank() && currentAccessToken != requestAccessToken) {
                return@synchronized response.request.newBuilder()
                    .header("Authorization", "Bearer $currentAccessToken")
                    .build()
            }

            val refreshToken = TokenManager.getRefreshToken(appContext)
            if (refreshToken.isNullOrBlank()) return@synchronized null

            val userId = TokenManager.getUserId(appContext)
            val refreshResponse = try {
                runBlocking { api.refresh(refreshToken) }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w("ApiClient", "토큰 재발급 요청 실패", e)
                null
            }

            val body = refreshResponse?.body()
            val newTokens = body?.data
            if (refreshResponse?.isSuccessful != true || body?.success != true || newTokens == null) {
                // 리프레시 실패 → 세션 종료. 이후 요청은 401 그대로 반환됨.
                if (BuildConfig.DEBUG) Log.w("ApiClient", "토큰 재발급 실패 — 로컬 세션 정리")
                TokenManager.clear(appContext)
                return@synchronized null
            }

            TokenManager.saveTokens(appContext, newTokens.accessToken, newTokens.refreshToken, userId)
            response.request.newBuilder()
                .header("Authorization", "Bearer ${newTokens.accessToken}")
                .build()
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior: Response? = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .authenticator(tokenAuthenticator)
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