package com.example.on_safe.ui.settings

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
import com.example.on_safe.data.fake.FakeUserRepository
import com.example.on_safe.data.repository.UserRepository

// 설정 화면 (알림 토글, 개인정보 수정, 비밀번호 변경, 로그아웃, 회원탈퇴)
class SettingsActivity : AppCompatActivity() {

    private lateinit var switchNotification: SwitchCompat
    private lateinit var switchSound: SwitchCompat
    private lateinit var switchVibration: SwitchCompat

    // 알림 OFF 상태에서 소리·진동 스위치를 프로그래밍 방식으로 되돌릴 때 리스너 중복 호출 방지
    private var isUpdatingToggles = false

    private lateinit var rowEditProfile: LinearLayout
    private lateinit var rowChangePassword: LinearLayout
    private lateinit var rowPrivacyPolicy: LinearLayout
    private lateinit var rowFaq: LinearLayout
    private lateinit var rowLogout: LinearLayout
    private lateinit var rowWithdraw: LinearLayout

    // btnBack은 레이아웃에서 제거됨
    // private lateinit var btnBack: ImageButton
    private lateinit var btnTutorial: android.widget.ImageView

    private lateinit var tabHistory: LinearLayout
    private lateinit var tabHome: LinearLayout

    private lateinit var tvUserName: TextView


    // TODO: API 연동 시 Real 구현체로 교체
    private val userRepository: UserRepository = FakeUserRepository()

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
        // btnBack은 레이아웃에서 제거됨
        switchNotification = findViewById(R.id.switchNotification)
        switchSound        = findViewById(R.id.switchSound)
        switchVibration    = findViewById(R.id.switchVibration)
        rowEditProfile     = findViewById(R.id.rowEditProfile)
        rowChangePassword  = findViewById(R.id.rowChangePassword)
        rowPrivacyPolicy   = findViewById(R.id.rowPrivacyPolicy)
        rowFaq             = findViewById(R.id.rowFaq)
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
        // 알림이 꺼져 있으면 소리·진동 시각적 비활성화 (alpha)
        switchSound.alpha     = if (notifyOn) 1f else 0.38f
        switchVibration.alpha = if (notifyOn) 1f else 0.38f
    }

    private fun loadUserName() {
        // TODO: 실제 이름 연동 시 "${이름} 보호자님" 형식으로 표시 (현재 Fake는 "보호자" 반환 → "보호자님"으로 표시됨)
        tvUserName.text = "${userRepository.getUserName()}님"
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
            "알림 권한이 '다시 묻지 않음'으로\n거부되었습니다. 앱 설정에서 직접 허용해주세요."

        dialog.findViewById<TextView>(R.id.btnPermDialogCancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.findViewById<TextView>(R.id.btnPermDialogConfirm).setOnClickListener {
            dialog.dismiss()
            openNotificationSettings.launch(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            )
        }
        dialog.show()
    }

    // 알림 ON → 소리·진동도 함께 ON / 알림 OFF → 소리·진동 함께 OFF·비활성화
    private fun setupToggleDependency() {
        switchNotification.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !isNotificationPermissionGranted()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                return@setOnCheckedChangeListener
            }
            switchSound.alpha     = if (isChecked) 1f else 0.38f
            switchVibration.alpha = if (isChecked) 1f else 0.38f
            isUpdatingToggles = true
            if (isChecked) {
                // 알림 ON → 소리·진동도 자동으로 ON (앱 기본 상태)
                switchSound.isChecked     = true
                switchVibration.isChecked = true
                settingsPrefs.edit()
                    .putBoolean("sound_enabled",     true)
                    .putBoolean("vibration_enabled", true)
                    .apply()
            } else {
                // 알림 OFF → 소리·진동도 함께 OFF
                switchSound.isChecked     = false
                switchVibration.isChecked = false
                settingsPrefs.edit()
                    .putBoolean("sound_enabled",     false)
                    .putBoolean("vibration_enabled", false)
                    .apply()
            }
            isUpdatingToggles = false
            settingsPrefs.edit().putBoolean("notify_enabled", isChecked).apply()
        }

        // 소리 스위치: 알림 OFF 상태에서 토글 시도 → 상태 되돌리고 토스트 (isUpdatingToggles로 무한 루프 방지)
        switchSound.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingToggles) return@setOnCheckedChangeListener
            if (!switchNotification.isChecked) {
                isUpdatingToggles = true
                switchSound.isChecked = !isChecked
                isUpdatingToggles = false
                Toast.makeText(this, "알림을 먼저 켜야 소리 설정을 변경할 수 있습니다.", Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }
            settingsPrefs.edit().putBoolean("sound_enabled", isChecked).apply()
        }

        // 진동 스위치: 동일 패턴
        switchVibration.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingToggles) return@setOnCheckedChangeListener
            if (!switchNotification.isChecked) {
                isUpdatingToggles = true
                switchVibration.isChecked = !isChecked
                isUpdatingToggles = false
                Toast.makeText(this, "알림을 먼저 켜야 진동 설정을 변경할 수 있습니다.", Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }
            settingsPrefs.edit().putBoolean("vibration_enabled", isChecked).apply()
        }
    }

    private fun setupClickListeners() {
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

        // TODO: FAQ 페이지 구현 (WebView 또는 전용 Activity)
        rowFaq.setOnClickListener {
            Toast.makeText(this, "자주 묻는 질문 준비 중입니다.", Toast.LENGTH_SHORT).show()
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

        // 바텀 네비: 홈 (설정은 오른쪽 탭 → 홈 복귀 시 왼쪽으로 슬라이드 아웃)
        tabHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            finish()
        }

        // 바텀 네비: 사고 이력 (왼쪽 탭 → 왼쪽에서 슬라이드 인)
        tabHistory.setOnClickListener {
            startActivity(Intent(this, com.example.on_safe.ui.history.AccidentHistoryActivity::class.java))
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
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
