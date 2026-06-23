package com.example.on_safe.ui.login

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.on_safe.MainActivity
import com.example.on_safe.R
import com.example.on_safe.ui.camera.CameraModeActivity

// 온보딩 권한 요청 화면 (TutorialActivity → 이 화면 → MainActivity or CameraModeActivity)
// selected_mode: 1 = 보호자 모드, 2 = 카메라 모드
class PermissionActivity : AppCompatActivity() {

    private var selectedMode = 1

    // 권한 요청 순서: 카메라 → 사진/영상 → 마이크 → 알림 (UI 목록 순서와 일치)
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
            if (areAllPermissionsGranted()) goToMain()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)

        selectedMode = intent.getIntExtra("selected_mode", 1)

        findViewById<Button>(R.id.btnAllow).setOnClickListener { requestAllPermissions() }
        findViewById<TextView>(R.id.tvSkip).setOnClickListener { goToMain() }

        if (areAllPermissionsGranted()) goToMain()
    }

    private fun requestAllPermissions() {
        if (areAllPermissionsGranted()) { goToMain(); return }
        requestPermissions.launch(requiredPermissions)
    }

    private fun handlePermissionResults(results: Map<String, Boolean>) {
        val denied = results.filter { !it.value }.keys
        if (denied.isEmpty()) { goToMain(); return }

        // 영구 거부된 항목이 하나라도 있으면 설정 화면으로 유도
        val permanentlyDenied = denied.any { !shouldShowRequestPermissionRationale(it) }
        if (permanentlyDenied) showGoToSettingsDialog()
        // else: 일시 거부 → 화면 유지, 사용자가 버튼 재클릭으로 재시도 가능
    }

    private fun areAllPermissionsGranted(): Boolean =
        requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun showGoToSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("권한 설정 필요")
            .setMessage("일부 권한이 '다시 묻지 않음'으로 거부되었습니다.\n앱 설정에서 직접 허용해주세요.")
            .setPositiveButton("설정으로 이동") { _, _ ->
                openSettings.launch(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                )
            }
            .setNegativeButton("나중에") { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }

    private fun goToMain() {
        val intent = when (selectedMode) {
            2 -> Intent(this, CameraModeActivity::class.java)
            else -> Intent(this, MainActivity::class.java)
        }.apply {
            putExtra("selected_mode", selectedMode)
            // 온보딩 백스택 전부 종료
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
