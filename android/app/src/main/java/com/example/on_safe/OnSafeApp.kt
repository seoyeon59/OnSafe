package com.example.on_safe

import android.app.Application

class OnSafeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.example.on_safe.network.ApiClient.init(this)
    }
}