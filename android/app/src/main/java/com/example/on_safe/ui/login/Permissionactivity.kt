package com.example.on_safe.ui.login

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.on_safe.R

// 온보딩 권한 요청 화면
// 플로우: 로그인 → 튜토리얼 → 권한 요청 → 모드 선택 → 메인 or 카메라
class PermissionActivity : AppCompatActivity() {

    // 권한 요청 순서: 카메라 → 사진/영상 → 마이크 → 알림 (UI 목록과 동일 순서)
    private val requiredPermissions: Array<String> by lazy {
        buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            handlePermissionResults(results)
        }

    // 앱 설정에서 돌아왔을 때 권한 재확인
    private val openSettings =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (areAllPermissionsGranted()) goToModeSelect()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)

        findViewById<Button>(R.id.btnAllow).setOnClickListener { requestAllPermissions() }
        findViewById<TextView>(R.id.tvSkip).setOnClickListener { goToModeSelect() }

        if (areAllPermissionsGranted()) goToModeSelect()
    }

    private fun requestAllPermissions() {
        if (areAllPermissionsGranted()) { goToModeSelect(); return }
        requestPermissions.launch(requiredPermissions)
    }

    private fun handlePermissionResults(results: Map<String, Boolean>) {
        val denied = results.filterValues { !it }.keys
        if (denied.isEmpty()) { goToModeSelect(); return }

        // 영구 거부 항목이 하나라도 있으면 설정 화면으로 유도
        val permanentlyDenied = denied.any { !shouldShowRequestPermissionRationale(it) }
        if (permanentlyDenied) showGoToSettingsDialog()
        // else: 일시 거부 → 화면 유지, 버튼 재클릭으로 재시도 가능
    }

    private fun areAllPermissionsGranted(): Boolean =
        requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun showGoToSettingsDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_permission_settings)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.85).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.setCanceledOnTouchOutside(false)

        dialog.findViewById<TextView>(R.id.tvPermDialogMessage).text =
            "일부 권한이 '다시 묻지 않음'으로\n거부되었습니다. 앱 설정에서 직접 허용해주세요."

        dialog.findViewById<TextView>(R.id.btnPermDialogCancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.findViewById<TextView>(R.id.btnPermDialogConfirm).setOnClickListener {
            dialog.dismiss()
            openSettings.launch(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            )
        }
        dialog.show()
    }

    // 권한 요청 완료(허용·거부·건너뛰기) → 모드 선택 이동, 온보딩 백스택 전부 종료
    private fun goToModeSelect() {
        startActivity(
            Intent(this, ModeSelectActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }
}
