package com.example.on_safe

import android.app.Application
import com.example.on_safe.util.CrashLogger

class OnSafeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        com.example.on_safe.network.ApiClient.init(this)
    }
}