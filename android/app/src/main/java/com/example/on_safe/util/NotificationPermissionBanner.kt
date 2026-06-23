package com.example.on_safe.util

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.on_safe.R

// 알림 권한 미동의 시 배너 표시/숨김 처리
object NotificationPermissionBanner {

    fun setup(activity: AppCompatActivity) {
        val banner = activity.findViewById<View>(R.id.bannerNotificationPermission) ?: return
        val btnAllow = banner.findViewById<TextView>(R.id.btnAllowNotification)

        updateVisibility(activity, banner)

        btnAllow.setOnClickListener {
            activity.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", activity.packageName, null)
                }
            )
        }
    }

    // onResume에서 호출 — 설정에서 돌아왔을 때 상태 갱신
    fun refresh(activity: AppCompatActivity) {
        val banner = activity.findViewById<View>(R.id.bannerNotificationPermission) ?: return
        updateVisibility(activity, banner)
    }

    private fun updateVisibility(activity: AppCompatActivity, banner: View) {
        banner.visibility = if (needsBanner(activity)) View.VISIBLE else View.GONE
    }

    private fun needsBanner(activity: AppCompatActivity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return ContextCompat.checkSelfPermission(
            activity, Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    }
}
