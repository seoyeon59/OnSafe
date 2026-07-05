package com.example.on_safe.ui.settings

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.example.on_safe.MainActivity
import com.example.on_safe.R
import com.example.on_safe.ResetPasswordActivity
import com.example.on_safe.util.TokenManager

// 설정 화면 (알림 토글, 개인정보 수정, 비밀번호 변경, 로그아웃, 회원탈퇴)
class SettingsActivity : AppCompatActivity() {

    private lateinit var switchNotification: SwitchCompat
    private lateinit var switchSound: SwitchCompat
    private lateinit var switchVibration: SwitchCompat

    private lateinit var rowEditProfile: LinearLayout
    private lateinit var rowChangePassword: LinearLayout
    private lateinit var rowPrivacyPolicy: LinearLayout
    private lateinit var rowLogout: LinearLayout
    private lateinit var rowWithdraw: LinearLayout

    private lateinit var btnBack: ImageButton
    private lateinit var btnTutorial: android.widget.ImageView

    private lateinit var tabHistory: LinearLayout
    private lateinit var tabHome: LinearLayout

    private lateinit var tvUserName: TextView

    // TODO: 현재 알림·소리·진동 토글 상태는 테스트용으로 기기 공용 SharedPreferences("settings")에 저장합니다.
    //       나중에 서버 API에서 사용자 ID 별로 설정을 관리하게 되면,
    //       아래 "settings" 키를 "settings_${userId}" 형태로 바꾸거나
    //       서버에서 받아온 값으로 초기화하는 방식으로 수정해주세요.
    private val settingsPrefs by lazy { getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        loadUserName()
        setupToggleDependency()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        // 시스템 설정에서 알림 권한이 취소된 경우 토글 강제 OFF 동기화
        if (!isNotificationPermissionGranted()) {
            if (switchNotification.isChecked) {
                switchNotification.isChecked = false
                // 저장값도 함께 업데이트 (리스너가 아직 attach 안 된 경우를 대비해 직접 저장)
                settingsPrefs.edit()
                    .putBoolean("notify_enabled",     false)
                    .putBoolean("sound_enabled",      false)
                    .putBoolean("vibration_enabled",  false)
                    .apply()
            }
        }
    }

    private fun initViews() {
        btnBack            = findViewById(R.id.btnBack)
        switchNotification = findViewById(R.id.switchNotification)
        switchSound        = findViewById(R.id.switchSound)
        switchVibration    = findViewById(R.id.switchVibration)
        rowEditProfile     = findViewById(R.id.rowEditProfile)
        rowChangePassword  = findViewById(R.id.rowChangePassword)
        rowPrivacyPolicy   = findViewById(R.id.rowPrivacyPolicy)
        rowLogout          = findViewById(R.id.rowLogout)
        rowWithdraw        = findViewById(R.id.rowWithdraw)
        tabHistory         = findViewById(R.id.tabHistory)
        tabHome            = findViewById(R.id.tabHome)
        tvUserName         = findViewById(R.id.tvUserName)
        btnTutorial        = findViewById(R.id.btnTutorial)

        // 저장된 토글 상태 복원
        val notifyOn = settingsPrefs.getBoolean("notify_enabled", true)
        switchNotification.isChecked = notifyOn
        switchSound.isChecked       = settingsPrefs.getBoolean("sound_enabled",     true)
        switchVibration.isChecked   = settingsPrefs.getBoolean("vibration_enabled", true)
        // 알림이 꺼져 있으면 소리·진동도 비활성화
        switchSound.isEnabled     = notifyOn
        switchVibration.isEnabled = notifyOn
    }

    private fun loadUserName() {
        // TODO: GET /user/profile → 이름 불러와 "${name} 보호자님" 표시
        tvUserName.text = "보호자님"
    }

    // 알림 권한 (API 33+)
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                switchNotification.isChecked = false
                val permanentlyDenied =
                    !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                if (permanentlyDenied) showNotificationSettingsDialog()
            }
        }

    private val openNotificationSettings =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (!isNotificationPermissionGranted()) {
                switchNotification.isChecked = false
            }
        }

    private fun isNotificationPermissionGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showNotificationSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("알림 권한 설정 필요")
            .setMessage("알림 권한이 '다시 묻지 않음'으로 거부되었습니다.\n앱 설정에서 직접 허용해주세요.")
            .setPositiveButton("설정으로 이동") { _, _ ->
                openNotificationSettings.launch(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                )
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // 알림 OFF 시 소리/진동 함께 비활성화, ON 시 API 33+ 권한 먼저 요청
    private fun setupToggleDependency() {
        switchNotification.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !isNotificationPermissionGranted()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                return@setOnCheckedChangeListener
            }
            switchSound.isEnabled     = isChecked
            switchVibration.isEnabled = isChecked
            if (!isChecked) {
                switchSound.isChecked     = false
                switchVibration.isChecked = false
                settingsPrefs.edit()
                    .putBoolean("sound_enabled",     false)
                    .putBoolean("vibration_enabled", false)
                    .apply()
            }
            settingsPrefs.edit().putBoolean("notify_enabled", isChecked).apply()
        }

        switchSound.setOnCheckedChangeListener { _, isChecked ->
            settingsPrefs.edit().putBoolean("sound_enabled", isChecked).apply()
        }

        switchVibration.setOnCheckedChangeListener { _, isChecked ->
            settingsPrefs.edit().putBoolean("vibration_enabled", isChecked).apply()
        }
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }

        // 개인정보 수정
        rowEditProfile.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }

        // 비밀번호 변경
        rowChangePassword.setOnClickListener {
            val userId = TokenManager.getUserId(this)
            val intent = Intent(this, ResetPasswordActivity::class.java).apply {
                putExtra("mode", ResetPasswordActivity.MODE_SETTINGS)
                putExtra("userId", userId)
            }
            startActivity(intent)
        }

        // TODO: 개인정보 처리방침 웹뷰 또는 브라우저 연동
        rowPrivacyPolicy.setOnClickListener {
            Toast.makeText(this, "개인정보 처리방침 준비 중", Toast.LENGTH_SHORT).show()
        }

        rowLogout.setOnClickListener {
            showLogoutConfirm()
        }

        // 회원탈퇴
        rowWithdraw.setOnClickListener {
            WithdrawAccountDialog(this) {
                handleWithdraw()
            }.show()
        }

        // 바텀 네비: 홈
        tabHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }

        // 바텀 네비: 사고 이력
        tabHistory.setOnClickListener {
            startActivity(Intent(this, com.example.on_safe.ui.history.AccidentHistoryActivity::class.java))
        }

        // 헤더 튜토리얼 버튼 (온보딩 완료 후에도 상시 진입)
        btnTutorial.setOnClickListener {
            startActivity(
                com.example.on_safe.ui.tutorial.TutorialActivity.intentFromSettings(this)
            )
        }
    }

    private fun showLogoutConfirm() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_logout)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.85).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.setCanceledOnTouchOutside(false)

        dialog.findViewById<TextView>(R.id.btnLogoutCancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.findViewById<TextView>(R.id.btnLogoutConfirm).setOnClickListener {
            dialog.dismiss()
            handleLogout()
        }
        dialog.show()
    }

    private fun handleLogout() {
        // TODO: 서버 로그아웃 API 호출 (POST /auth/logout)
        TokenManager.clear(this)
        Toast.makeText(this, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()
        startActivity(
            Intent(this, com.example.on_safe.ui.login.LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }

    private fun handleWithdraw() {
        // TODO: 서버 회원탈퇴 API 호출 (DELETE /user)
        TokenManager.clear(this)
        Toast.makeText(this, "회원탈퇴가 완료되었습니다.", Toast.LENGTH_SHORT).show()
        startActivity(
            Intent(this, com.example.on_safe.ui.login.LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }
}
