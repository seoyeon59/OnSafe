package com.example.on_safe.network.dto

data class NotificationSettingsRequest(
    val notificationEnabled: Boolean? = null,
    val soundEnabled: Boolean? = null,
    val vibrationEnabled: Boolean? = null
)

data class NotificationSettingsResponse(
    val notificationEnabled: Boolean,
    val soundEnabled: Boolean,
    val vibrationEnabled: Boolean
)

// 마케팅 수신 동의 — GET/PUT /api/settings/marketing/{userId} (v4.2 신규)
data class MarketingConsentRequest(val consent: Boolean)

data class MarketingConsentResponse(
    val consent: Boolean,
    val consentedAt: String?
)