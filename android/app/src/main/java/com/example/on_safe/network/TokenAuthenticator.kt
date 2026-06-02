package com.example.on_safe.network

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.example.on_safe.ui.login.LoginActivity
import com.google.gson.GsonBuilder
import com.google.gson.FieldNamingPolicy
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class TokenAuthenticator(private val context: Context) : Authenticator {

    // 별도 클라이언트로 refresh 호출 — Authenticator 루프 방지
    private val refreshApi: ApiService by lazy {
        val gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
        Retrofit.Builder()
            .baseUrl(ApiClient.BASE_URL)
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null

        val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
        val refreshToken = prefs.getString("refresh_token", "") ?: ""
        if (refreshToken.isEmpty()) {
            clearAndNavigateToLogin(prefs)
            return null
        }

        val refreshResponse = runBlocking {
            try { refreshApi.refreshToken(refreshToken) } catch (e: Exception) { null }
        }

        return if (refreshResponse?.isSuccessful == true) {
            val data = refreshResponse.body()?.data ?: run {
                clearAndNavigateToLogin(prefs)
                return null
            }
            prefs.edit()
                .putString("access_token", data.accessToken)
                .putString("refresh_token", data.refreshToken)
                .apply()
            response.request.newBuilder()
                .header("Authorization", "Bearer ${data.accessToken}")
                .build()
        } else {
            clearAndNavigateToLogin(prefs)
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) { count++; prior = prior.priorResponse }
        return count
    }

    private fun clearAndNavigateToLogin(prefs: SharedPreferences) {
        prefs.edit().clear().apply()
        context.startActivity(
            Intent(context, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }
}
