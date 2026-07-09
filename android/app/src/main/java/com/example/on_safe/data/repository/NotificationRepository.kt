package com.example.on_safe.data.repository

import com.example.on_safe.ui.notification.NotificationItem

interface NotificationRepository {
    fun getNotifications(): MutableList<NotificationItem>
}