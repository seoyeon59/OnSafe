package com.example.on_safe.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.example.on_safe.MainActivity
import com.example.on_safe.R
import com.example.on_safe.ResetPasswordActivity

/**
 * 설정 화면.
 * ─ 알림 설정 (알림 / 소리 / 진동 토글)
 * ─ 개인정보 수정 → EditProfileActivity
 * ─ 비밀번호 변경 → ResetPasswordActivity (MODE_SETTINGS)
 * ─ 개인정보 처리방침 (TODO: 웹뷰 또는 외부 링크)
 * ─ 로그아웃
 * ─ 회원 탈퇴 → WithdrawAccountDialog
 */
class SettingsActivity : AppCompatActivity() {

    // 알림 토글
    private lateinit var switchNotification: SwitchCompat
    private lateinit var switchSound: SwitchCompat
    private lateinit var switchVibration: SwitchCompat

    // 메뉴 행
    private lateinit var rowEditProfile: LinearLayout
    private lateinit var rowChangePassword: LinearLayout
    private lateinit var rowPrivacyPolicy: LinearLayout
    private lateinit var rowLogout: LinearLayout
    private lateinit var rowWithdraw: LinearLayout

    // 헤더
    private lateinit var btnBack: ImageButton

    // 바텀 내비
    private lateinit var tabHistory: LinearLayout
    private lateinit var tabHome: LinearLayout

    // 사용자 이름 표시
    private lateinit var tvUserName: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        loadUserName()
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
    }

    /**
     * TODO: SharedPreferences / 서버에서 사용자 이름을 불러와 표시한다.
     */
    private fun loadUserName() {
        // val prefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        // val name = prefs.getString("name", "") ?: ""
        // tvUserName.text = if (name.isNotEmpty()) "${name} 보호자님" else "보호자님"
        tvUserName.text = "보호자님"
    }

    /**
     * 메인 알림 토글이 꺼지면 소리/진동도 함께 비활성화한다.
     */
    private fun setupToggleDependency() {
        switchNotification.setOnCheckedChangeListener { _, isChecked ->
            switchSound.isEnabled = isChecked
            switchVibration.isEnabled = isChecked
            if (!isChecked) {
                switchSound.isChecked = false
                switchVibration.isChecked = false
            }
        }

        // TODO: SharedPreferences에서 토글 상태를 불러와 복원한다.
        // val prefs = getSharedPreferences("notification_prefs", MODE_PRIVATE)
        // switchNotification.isChecked = prefs.getBoolean("notification", true)
        // switchSound.isChecked = prefs.getBoolean("sound", true)
        // switchVibration.isChecked = prefs.getBoolean("vibration", true)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }

        // ── 개인정보 수정
        rowEditProfile.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }

        // ── 비밀번호 변경 (기존 ResetPasswordActivity, MODE_SETTINGS)
        rowChangePassword.setOnClickListener {
            val intent = Intent(this, ResetPasswordActivity::class.java).apply {
                putExtra("mode", ResetPasswordActivity.MODE_SETTINGS)
            }
            startActivity(intent)
        }

        // ── 개인정보 처리방침 (TODO: 웹뷰 또는 브라우저로 이동)
        rowPrivacyPolicy.setOnClickListener {
            Toast.makeText(this, "개인정보 처리방침 준비 중", Toast.LENGTH_SHORT).show()
            // val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://your-privacy-url"))
            // startActivity(intent)
        }

        // ── 로그아웃
        rowLogout.setOnClickListener {
            showLogoutConfirm()
        }

        // ── 회원탈퇴
        rowWithdraw.setOnClickListener {
            WithdrawAccountDialog(this) {
                handleWithdraw()
            }.show()
        }

        // ── 바텀 네비: 홈
        tabHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }

        // ── 바텀 네비: 사고 이력
        tabHistory.setOnClickListener {
            Toast.makeText(this, "사고 이력 준비 중", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLogoutConfirm() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("로그아웃")
            .setMessage("로그아웃 하시겠습니까?")
            .setPositiveButton("로그아웃") { _, _ ->
                handleLogout()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun handleLogout() {
        // TODO: 서버 로그아웃 API 호출 + 로컬 토큰/세션 제거
        Toast.makeText(this, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()

        // 로그인 화면으로 이동 (백스택 전부 제거)
        val intent = Intent(this, com.example.on_safe.ui.login.LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }

    private fun handleWithdraw() {
        // TODO: 서버 회원탈퇴 API 호출 + 로컬 데이터 전체 삭제
        Toast.makeText(this, "회원탈퇴가 완료되었습니다.", Toast.LENGTH_SHORT).show()

        val intent = Intent(this, com.example.on_safe.ui.login.LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }
}
