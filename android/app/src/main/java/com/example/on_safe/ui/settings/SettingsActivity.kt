package com.example.on_safe.ui.settings

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.on_safe.MainActivity
import com.example.on_safe.R
import com.example.on_safe.ResetPasswordActivity
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.NotificationSettingsRequest
import com.example.on_safe.network.dto.VerifyPasswordRequest
import kotlinx.coroutines.launch

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

    // 초기 설정 로드 중 토글 변경 이벤트가 API를 중복 호출하지 않도록 방지
    private var isLoadingSettings = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        loadUserName()
        loadNotificationSettings()
        setupToggleDependency()
        setupClickListeners()
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
    }

    private fun loadUserName() {
        val token  = getAuthToken()
        val userId = getUserId()
        if (token.isEmpty() || userId.isEmpty()) return

        lifecycleScope.launch {
            try {
                val response = ApiClient.api.getUser("Bearer $token", userId)
                if (response.isSuccessful) {
                    val name = response.body()?.data?.name ?: return@launch
                    tvUserName.text = "${name} 보호자님"
                }
            } catch (e: Exception) {
                // 네트워크 실패 시 기본 텍스트 유지
            }
        }
    }

    private fun loadNotificationSettings() {
        val token  = getAuthToken()
        val userId = getUserId()
        if (token.isEmpty() || userId.isEmpty()) return

        lifecycleScope.launch {
            try {
                val response = ApiClient.api.getNotificationSettings("Bearer $token", userId)
                if (response.isSuccessful) {
                    val settings = response.body()?.data ?: return@launch
                    isLoadingSettings = true
                    switchNotification.isChecked = settings.notificationEnabled
                    switchSound.isChecked        = settings.soundEnabled
                    switchVibration.isChecked    = settings.vibrationEnabled
                    switchSound.isEnabled        = settings.notificationEnabled
                    switchVibration.isEnabled    = settings.notificationEnabled
                    isLoadingSettings = false
                }
            } catch (e: Exception) {
                // 네트워크 실패 시 기본 상태(모두 ON) 유지
            }
        }
    }

    private fun saveNotificationSettings() {
        val token  = getAuthToken()
        val userId = getUserId()
        if (token.isEmpty() || userId.isEmpty()) return

        lifecycleScope.launch {
            try {
                ApiClient.api.updateNotificationSettings(
                    "Bearer $token",
                    userId,
                    NotificationSettingsRequest(
                        notificationEnabled = switchNotification.isChecked,
                        soundEnabled        = switchSound.isChecked,
                        vibrationEnabled    = switchVibration.isChecked
                    )
                )
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "설정 저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
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
            }
            if (!isLoadingSettings) saveNotificationSettings()
        }

        switchSound.setOnCheckedChangeListener { _, _ ->
            if (!isLoadingSettings) saveNotificationSettings()
        }

        switchVibration.setOnCheckedChangeListener { _, _ ->
            if (!isLoadingSettings) saveNotificationSettings()
        }
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }

        rowEditProfile.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }

        rowChangePassword.setOnClickListener {
            val userId = getUserId()
            val intent = Intent(this, ResetPasswordActivity::class.java).apply {
                putExtra("mode", ResetPasswordActivity.MODE_SETTINGS)
                putExtra("userId", userId)
            }
            startActivity(intent)
        }

        rowPrivacyPolicy.setOnClickListener {
            Toast.makeText(this, "개인정보 처리방침 준비 중", Toast.LENGTH_SHORT).show()
        }

        rowLogout.setOnClickListener { showVerifyForLogout() }

        rowWithdraw.setOnClickListener {
            WithdrawAccountDialog(this) { handleWithdraw() }.show()
        }

        tabHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }

        tabHistory.setOnClickListener {
            startActivity(Intent(this, com.example.on_safe.ui.history.AccidentHistoryActivity::class.java))
        }

        btnTutorial.setOnClickListener {
            startActivity(
                com.example.on_safe.ui.tutorial.TutorialActivity.intentFromSettings(this)
            )
        }
    }

    // 로그아웃 전 비밀번호 본인 확인 (VerifyPasswordDialog 재사용)
    private fun showVerifyForLogout() {
        VerifyPasswordDialog(
            context   = this,
            onConfirm = { password -> verifyAndLogout(password) },
            onCancel  = {}
        ).show()
    }

    private fun verifyAndLogout(password: String) {
        val token  = getAuthToken()
        val userId = getUserId()
        lifecycleScope.launch {
            try {
                val response = ApiClient.api.verifyPassword(
                    "Bearer $token",
                    userId,
                    VerifyPasswordRequest(currentPassword = password)
                )
                if (response.isSuccessful) {
                    handleLogout()
                } else {
                    val msg = ApiClient.parseErrorMessage(response.errorBody(), "비밀번호가 일치하지 않습니다.")
                    Toast.makeText(this@SettingsActivity, msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleLogout() {
        val token = getAuthToken()
        lifecycleScope.launch {
            try {
                if (token.isNotEmpty()) {
                    ApiClient.api.logout("Bearer $token")
                }
            } catch (e: Exception) {
                // API 실패해도 로컬 세션은 반드시 삭제
            } finally {
                clearSession()
                navigateToLogin()
            }
        }
    }

    private fun handleWithdraw() {
        val token  = getAuthToken()
        val userId = getUserId()
        lifecycleScope.launch {
            try {
                if (token.isNotEmpty() && userId.isNotEmpty()) {
                    val response = ApiClient.api.deleteUser("Bearer $token", userId)
                    if (!response.isSuccessful) {
                        val msg = ApiClient.parseErrorMessage(response.errorBody(), "회원탈퇴에 실패했습니다.")
                        Toast.makeText(this@SettingsActivity, msg, Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            clearSession()
            navigateToLogin()
        }
    }

    private fun getAuthToken(): String =
        getSharedPreferences("auth", Context.MODE_PRIVATE).getString("access_token", "") ?: ""

    private fun getUserId(): String =
        getSharedPreferences("auth", Context.MODE_PRIVATE).getString("user_id", "") ?: ""

    private fun clearSession() {
        getSharedPreferences("auth", Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, com.example.on_safe.ui.login.LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }
}
