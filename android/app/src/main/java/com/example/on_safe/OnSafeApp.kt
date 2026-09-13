package com.example.on_safe

import android.app.Application
import com.example.on_safe.messaging.FcmTokenRegistrar
import com.example.on_safe.messaging.PushNotifications
import com.example.on_safe.network.ApiClient
import com.example.on_safe.util.CrashLogger

class OnSafeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 초기화 중 예외까지 포착하기 위한 우선 설치
        CrashLogger.install(this)
        ApiClient.init(this)

        // 알림 채널은 표시 전에 존재해야 함 — 시작 시 1회 생성(있으면 그대로 둠).
        PushNotifications.createChannels(this)
        // 자동 로그인 사용자의 토큰을 서버와 재동기화 — 앱 재설치·토큰 롤오버 후 최신화.
        // (미로그인이거나 google-services.json 미설정이면 내부에서 안전하게 건너뜀)
        FcmTokenRegistrar.registerIfLoggedIn(this)
    }
}