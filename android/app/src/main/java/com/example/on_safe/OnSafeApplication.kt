package com.example.on_safe

import android.app.Application
import com.example.on_safe.network.ApiClient

class OnSafeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiClient.init(this)
    }
}
