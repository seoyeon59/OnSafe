package com.example.on_safe.ui.login

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.on_safe.R

class PermissionActivity : AppCompatActivity() {

    private lateinit var btnAllow: Button
    private lateinit var tvSkip: TextView
    private lateinit var itemCamera: LinearLayout

    // ModeSelectActivity에서 전달받은 모드
    // 1 = 보호자, 2 = 카메라
    private var selectedMode = 1

    // 알림 권한 요청
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (selectedMode == 2) {
                // 카메라 모드면 카메라 권한도 요청
                requestCameraPermission.launch(Manifest.permission.CAMERA)
            } else {
                goToMain()
            }
        }

    // 카메라 권한 요청
    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            goToMain()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)

        btnAllow = findViewById(R.id.btnAllow)
        tvSkip = findViewById(R.id.tvSkip)
        itemCamera = findViewById(R.id.itemCamera)

        // ModeSelectActivity에서 모드 전달받기
        selectedMode = intent.getIntExtra("selected_mode", 1)

        // 카메라 모드면 카메라 권한 항목 표시
        if (selectedMode == 2) {
            itemCamera.visibility = View.VISIBLE
        }

        // 알림 허용하기
        btnAllow.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Android 13 미만은 알림 권한 자동 허용
                if (selectedMode == 2) {
                    requestCameraPermission.launch(Manifest.permission.CAMERA)
                } else {
                    goToMain()
                }
            }
        }

        // 나중에 설정하기
        tvSkip.setOnClickListener {
            goToMain()
        }
    }

    private fun goToMain() {
        // TODO: MainActivity로 이동
        // startActivity(Intent(this, MainActivity::class.java))
        // finishAffinity() // 로그인 스택 전부 종료
    }
}