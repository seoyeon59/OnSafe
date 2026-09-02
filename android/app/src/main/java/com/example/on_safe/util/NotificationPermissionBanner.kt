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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.on_safe.R

// 알림 권한 미동의 시 배너 표시/숨김 처리
object NotificationPermissionBanner {

    fun setup(activity: AppCompatActivity) {
        val banner = activity.findViewById<View>(R.id.bannerNotificationPermission) ?: return
        banner.isVisible = needsBanner(activity)
        banner.findViewById<TextView>(R.id.btnAllowNotification)
            .setOnClickListener { requestOrOpenSettings(activity) }
    }

    // onResume에서 호출 — 권한 상태 변경 시 배너 가시성 갱신
    fun refresh(activity: AppCompatActivity) {
        val banner = activity.findViewById<View>(R.id.bannerNotificationPermission) ?: return
        banner.isVisible = needsBanner(activity)
    }

    private fun needsBanner(activity: AppCompatActivity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return ContextCompat.checkSelfPermission(
            activity, Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    }

    private fun requestOrOpenSettings(activity: AppCompatActivity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        if (activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            // 일시 거부 상태 → 시스템 다이얼로그로 직접 요청 (앱 화면을 벗어나지 않음)
            // 다이얼로그 종료 후 Activity.onResume → refresh() 호출로 배너 상태 자동 갱신
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                0
            )
        } else {
            // 영구 거부 상태 → 앱 내 다이얼로그가 표시되지 않으므로 시스템 설정으로 이동
            // (건너뛰기로 온보딩을 넘긴 경우도 이 경로를 통해 설정에서 허용 가능)
            activity.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", activity.packageName, null)
                }
            )
        }
    }
}
