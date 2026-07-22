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