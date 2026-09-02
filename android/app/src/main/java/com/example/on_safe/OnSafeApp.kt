package com.example.on_safe

import android.app.Application
import com.example.on_safe.network.ApiClient
import com.example.on_safe.util.CrashLogger

class OnSafeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 초기화 중 예외까지 포착하기 위한 우선 설치
        CrashLogger.install(this)
        ApiClient.init(this)
    }
}