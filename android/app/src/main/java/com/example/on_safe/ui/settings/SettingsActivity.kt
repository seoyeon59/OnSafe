package com.example.on_safe.ui.settings

import android.Manifest
import android.app.Dialog
import android.content.Context
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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import com.example.on_safe.MainActivity
import com.example.on_safe.R
import com.example.on_safe.ResetPasswordActivity
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.NotificationSettingsRequest
import com.example.on_safe.util.TokenManager
import kotlinx.coroutines.launch
import com.example.on_safe.data.fake.FakeUserRepository
import com.example.on_safe.data.repository.UserRepository

// 설정 화면 (알림 토글, 개인정보 수정, 비밀번호 변경, 로그아웃, 회원탈퇴)
//
// [알림 토글 저장 전략]
// 서버(백엔드 DB)가 진실의 원천이며, SharedPreferences("settings")는 오프라인 캐시.
// 진입 시 캐시를 즉시 보여주고 → 서버에서 최신 값을 받아 캐시·UI를 갱신한다.
// 사용자 조작 시엔 캐시를 먼저 갱신(optimistic)하고 → 서버에 PUT.
// PUT 실패 시엔 캐시를 그대로 두어 다음 서버 GET 성공 시점에 자동 재동기화.
class SettingsActivity : AppCompatActivity() {

    private lateinit var switchNotification: SwitchCompat
    private lateinit var switchSound: SwitchCompat
    private lateinit var switchVibration: SwitchCompat
    private lateinit var ivSoundIcon: ImageView
    private lateinit var ivVibrationIcon: ImageView
    private lateinit var ivNotificationIcon: ImageView

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

    // 프로그래밍적으로 토글을 세팅할 때 리스너 재발화(재-PUT) 방지
    private var suppressToggleListeners = false

    // 빠르게 연속으로 토글할 때 같은 항목의 이전 PUT이 늦게 도착해 최신 상태를 덮어쓰지 않도록,
    // 항목(알림/소리/진동)별로 별도 Job을 두고 같은 항목이 다시 바뀔 때만 이전 요청을 취소한다.
    // (하나의 Job만 공유하면 예: 진동 저장 중 소리를 바로 바꿨을 때 진동 저장이 취소되어 버리는 새 문제가 생김)
    private var notificationUpdateJob: Job? = null
    private var soundUpdateJob: Job? = null
    private var vibrationUpdateJob: Job? = null

    private val settingsPrefs by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    // 테스트용 로컬 더미 저장소 (로그인 정보 없을 때 이름 표시)
    private val userRepository: UserRepository = FakeUserRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        setupToggleDependency()
        setupClickListeners()

        loadUserName()
        loadNotificationSettings()
    }

    override fun onResume() {
        super.onResume()
        // 시스템 설정에서 알림 권한이 취소된 경우 토글 강제 OFF 동기화
        // (프로그래밍 방식 변경이지만 리스너 발화가 필요 — 캐시·서버 모두 반영)
        if (!isNotificationPermissionGranted() && switchNotification.isChecked) {
            switchNotification.isChecked = false
        }
    }

    private fun initViews() {
        // btnBack은 레이아웃에서 제거됨
        switchNotification = findViewById(R.id.switchNotification)
        switchSound        = findViewById(R.id.switchSound)
        switchVibration    = findViewById(R.id.switchVibration)
        ivSoundIcon        = findViewById(R.id.ivSoundIcon)
        ivVibrationIcon    = findViewById(R.id.ivVibrationIcon)
        ivNotificationIcon = findViewById(R.id.ivNotificationIcon)
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

        // 캐시에서 즉시 복원 (오프라인/서버 응답 전에도 정확한 마지막 값 표시)
        applyNotificationValues(
            notification = settingsPrefs.getBoolean(KEY_NOTIFY, true),
            sound = settingsPrefs.getBoolean(KEY_SOUND, true),
            vibration = settingsPrefs.getBoolean(KEY_VIBRATION, true)
        )
    }

    // UI 3개 토글과 활성 상태를 리스너 억제 상태로 일괄 적용
    private fun applyNotificationValues(notification: Boolean, sound: Boolean, vibration: Boolean) {
        suppressToggleListeners = true
        switchNotification.isChecked = notification
        switchSound.isChecked = sound
        switchVibration.isChecked = vibration
        switchSound.isEnabled = notification
        switchVibration.isEnabled = notification
        updateNotificationIcon(notification)
        updateSoundIcon(sound)
        updateVibrationIcon(vibration)
        suppressToggleListeners = false
    }

    // 알림 마스터 스위치 상태에 맞춰 벨 아이콘을 울리는 모양/기본 모양으로 교체
    private fun updateNotificationIcon(on: Boolean) {
        ivNotificationIcon.setImageResource(if (on) R.drawable.ic_notification_ringing else R.drawable.ic_notification)
    }

    // 소리/진동 스위치 상태에 맞춰 행 아이콘도 on/off 아이콘으로 교체
    private fun updateSoundIcon(on: Boolean) {
        ivSoundIcon.setImageResource(if (on) R.drawable.ic_volume else R.drawable.ic_volume_off)
    }

    private fun updateVibrationIcon(on: Boolean) {
        ivVibrationIcon.setImageResource(if (on) R.drawable.ic_vibration else R.drawable.ic_vibration_off)
    }

    // 캐시 저장 (서버 저장과 별개의 단순 로컬 미러)
    private fun writeCache(notification: Boolean?, sound: Boolean?, vibration: Boolean?) {
        val editor = settingsPrefs.edit()
        if (notification != null) editor.putBoolean(KEY_NOTIFY, notification)
        if (sound != null) editor.putBoolean(KEY_SOUND, sound)
        if (vibration != null) editor.putBoolean(KEY_VIBRATION, vibration)
        editor.apply()
    }

    private fun loadUserName() {
        val userId = TokenManager.getUserId(this)
        if (userId.isBlank()) {
            tvUserName.text = "${userRepository.getUserName()}님"
            return
        }
        lifecycleScope.launch {
            try {
                val response = ApiClient.api.getUser(userId)
                val body = response.body()
                if (response.isSuccessful && body?.success == true && body.data != null) {
                    tvUserName.text = "${body.data.name} 보호자님"
                } else {
                    tvUserName.text = "보호자님"
                }
            } catch (_: Exception) {
                tvUserName.text = "보호자님"
            }
        }
    }

    // 서버 최신 값으로 UI + 캐시 갱신. 실패 시 캐시로 표시한 값 그대로 둠.
    private fun loadNotificationSettings() {
        val userId = TokenManager.getUserId(this)
        if (userId.isBlank()) return
        lifecycleScope.launch {
            try {
                val response = ApiClient.api.getNotificationSettings(userId)
                val body = response.body()
                if (response.isSuccessful && body?.success == true && body.data != null) {
                    val data = body.data
                    applyNotificationValues(
                        notification = data.notificationEnabled,
                        sound = data.soundEnabled,
                        vibration = data.vibrationEnabled
                    )
                    writeCache(data.notificationEnabled, data.soundEnabled, data.vibrationEnabled)
                }
            } catch (_: Exception) {
                // 조회 실패 → 캐시로 이미 표시된 값 유지
            }
        }
    }

    // 사용자 조작 시 서버 PUT (부분 업데이트). 실패해도 캐시는 갱신된 상태 유지.
    // notification이 null이 아니면(마스터 스위치) notificationUpdateJob을, 그 외엔 바뀐 항목(sound/vibration) 전용 Job을 사용
    private fun updateNotificationSetting(
        notification: Boolean? = null,
        sound: Boolean? = null,
        vibration: Boolean? = null
    ) {
        val userId = TokenManager.getUserId(this)
        if (userId.isBlank()) return

        val newJob = lifecycleScope.launch {
            try {
                val response = ApiClient.api.updateNotificationSettings(
                    userId,
                    NotificationSettingsRequest(
                        notificationEnabled = notification,
                        soundEnabled = sound,
                        vibrationEnabled = vibration
                    )
                )
                if (!response.isSuccessful || response.body()?.success != true) {
                    Toast.makeText(this@SettingsActivity, "설정 저장 실패", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(this@SettingsActivity, "설정 저장 실패", Toast.LENGTH_SHORT).show()
            }
        }

        when {
            notification != null -> {
                notificationUpdateJob?.cancel()
                notificationUpdateJob = newJob
            }
            sound != null -> {
                soundUpdateJob?.cancel()
                soundUpdateJob = newJob
            }
            vibration != null -> {
                vibrationUpdateJob?.cancel()
                vibrationUpdateJob = newJob
            }
        }
    }

    // 알림 권한 (API 33+)
    // 권한 허용 시 반드시 turnNotificationOn()을 거쳐야 소리/진동 활성화 + 서버 반영까지 완료됨
    // (스위치의 isChecked만 true로 두면 겉보기엔 켜졌지만 실제로는 아무 것도 저장되지 않는 상태가 됨)
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                turnNotificationOn()
            } else {
                val permanentlyDenied =
                    !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                if (permanentlyDenied) showNotificationSettingsDialog()
            }
        }

    private val openNotificationSettings =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (isNotificationPermissionGranted()) {
                turnNotificationOn()
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

    // 알림 마스터 스위치 ON — 소리·진동도 함께 켜고, 서버에 반영
    // 스위치 직접 조작 / 권한 허용 콜백(requestNotificationPermission, openNotificationSettings) 양쪽에서 공용으로 호출
    private fun turnNotificationOn() {
        suppressToggleListeners = true
        switchNotification.isChecked = true
        switchSound.isEnabled = true
        switchVibration.isEnabled = true
        switchSound.isChecked = true
        switchVibration.isChecked = true
        updateNotificationIcon(true)
        updateSoundIcon(true)
        updateVibrationIcon(true)
        suppressToggleListeners = false
        writeCache(notification = true, sound = true, vibration = true)
        updateNotificationSetting(notification = true, sound = true, vibration = true)
    }

    // 알림 마스터 스위치 OFF — 소리·진동도 함께 끄고 비활성화, 서버에 반영
    private fun turnNotificationOff() {
        suppressToggleListeners = true
        switchNotification.isChecked = false
        switchSound.isEnabled = false
        switchVibration.isEnabled = false
        switchSound.isChecked = false
        switchVibration.isChecked = false
        updateNotificationIcon(false)
        updateSoundIcon(false)
        updateVibrationIcon(false)
        suppressToggleListeners = false
        writeCache(notification = false, sound = false, vibration = false)
        updateNotificationSetting(notification = false, sound = false, vibration = false)
    }

    // 알림 ON → 소리·진동도 함께 ON / 알림 OFF → 소리·진동 함께 OFF·비활성화
    private fun setupToggleDependency() {
        switchNotification.setOnCheckedChangeListener { _, isChecked ->
            if (suppressToggleListeners) return@setOnCheckedChangeListener

            if (isChecked && !isNotificationPermissionGranted()) {
                // 권한 응답이 오기 전까지는 스위치를 다시 꺼둔다 — 응답 결과는
                // requestNotificationPermission 콜백에서 turnNotificationOn()으로 최종 반영됨
                suppressToggleListeners = true
                switchNotification.isChecked = false
                suppressToggleListeners = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                return@setOnCheckedChangeListener
            }

            if (isChecked) turnNotificationOn() else turnNotificationOff()
        }

        switchSound.setOnCheckedChangeListener { _, isChecked ->
            if (suppressToggleListeners) return@setOnCheckedChangeListener
            updateSoundIcon(isChecked)
            writeCache(notification = null, sound = isChecked, vibration = null)
            updateNotificationSetting(sound = isChecked)
        }

        switchVibration.setOnCheckedChangeListener { _, isChecked ->
            if (suppressToggleListeners) return@setOnCheckedChangeListener
            updateVibrationIcon(isChecked)
            writeCache(notification = null, sound = null, vibration = isChecked)
            updateNotificationSetting(vibration = isChecked)
        }
    }

    private fun setupClickListeners() {
        // 개인정보 수정
        rowEditProfile.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
            overridePendingTransition(R.anim.detail_enter, R.anim.detail_exit)
        }

        // 비밀번호 변경
        rowChangePassword.setOnClickListener {
            val userId = TokenManager.getUserId(this)
            val intent = Intent(this, ResetPasswordActivity::class.java).apply {
                putExtra("mode", ResetPasswordActivity.MODE_SETTINGS)
                putExtra("userId", userId)
            }
            startActivity(intent)
            overridePendingTransition(R.anim.detail_enter, R.anim.detail_exit)
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

        // 바텀 네비: 사고 이력 — 홈을 건너뛰고 이동하므로 더 빠른 전환으로 "스쳐 지나가는" 느낌을 줌
        // 탭 화면끼리는 항상 finish()로 이전 탭을 정리 → 뒤로가기 눌러도 다른 탭이 쌓여있지 않음
        tabHistory.setOnClickListener {
            startActivity(Intent(this, com.example.on_safe.ui.history.AccidentHistoryActivity::class.java))
            overridePendingTransition(R.anim.slide_in_left_fast, R.anim.slide_out_right_fast)
            finish()
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

    // 서버 로그아웃 → 로컬 토큰 정리 → 로그인 화면 이동
    // 서버 호출이 실패해도 로컬 로그아웃은 진행 (사용자 관점에서 항상 성공해야 함)
    private fun handleLogout() {
        lifecycleScope.launch {
            try {
                ApiClient.api.logout()
            } catch (_: Exception) {
                // 무시하고 로컬 정리로 진행
            }
            TokenManager.clear(this@SettingsActivity)
            Toast.makeText(this@SettingsActivity, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()
            startActivity(
                Intent(this@SettingsActivity, com.example.on_safe.ui.login.LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
        }
    }

    // 서버 회원탈퇴 성공 시에만 로컬 정리. 실패 시 계정이 살아있으므로 상태 유지.
    private fun handleWithdraw() {
        val userId = TokenManager.getUserId(this)
        if (userId.isBlank()) {
            Toast.makeText(this, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                val response = ApiClient.api.deleteUser(userId)
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    TokenManager.clear(this@SettingsActivity)
                    Toast.makeText(this@SettingsActivity, "회원탈퇴가 완료되었습니다.", Toast.LENGTH_SHORT).show()
                    startActivity(
                        Intent(this@SettingsActivity, com.example.on_safe.ui.login.LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                    )
                } else {
                    val message = ApiClient.parseErrorMessage(response.errorBody(), "회원탈퇴에 실패했습니다.")
                    Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(this@SettingsActivity, "네트워크 오류로 회원탈퇴에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "settings"
        private const val KEY_NOTIFY = "notify_enabled"
        private const val KEY_SOUND = "sound_enabled"
        private const val KEY_VIBRATION = "vibration_enabled"
    }
}
