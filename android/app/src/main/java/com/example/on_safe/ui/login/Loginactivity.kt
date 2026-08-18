package com.example.on_safe.ui.login

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.LinkMovementMethod
import android.text.method.PasswordTransformationMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.on_safe.BuildConfig
import com.example.on_safe.R
import com.example.on_safe.ui.tutorial.TutorialActivity
import com.example.on_safe.util.TokenManager

class LoginActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels()

    private lateinit var etId: EditText
    private lateinit var etPw: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnTogglePw: ImageButton
    private lateinit var tvFindId: TextView
    private lateinit var tvFindPw: TextView
    private lateinit var tvRegister: TextView
    private lateinit var tvLoginError: TextView
    private lateinit var pbLoading: ProgressBar

    private var isPwVisible = false

    private val COLOR_RED = 0xFFEF4444.toInt()
    private val COLOR_NORMAL = 0xFFF4F7FB.toInt()
    private var dpScale = 0f
    private var cornerPx = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 디버그 빌드 여부 — FLAG_DEBUGGABLE은 릴리즈 서명 빌드에서 자동으로 제거됨
        val isDebug = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

        // ── 자동 로그인 체크 ──────────────────────────────────────────────────────
        // 토큰이 살아있고 마지막 로그인으로부터 30일 이내 → 로그인 화면 건너뜀
        // TODO: 테스팅 완료 후 isDebug 조건 제거하여 자동 로그인 활성화
        if (!isDebug && TokenManager.isLoggedIn(this) && !TokenManager.isSessionExpired(this)) {
            val next = if (!TutorialActivity.isTutorialShown(this)) {
                TutorialActivity.intentForLogin(this)
            } else {
                Intent(this, ModeSelectActivity::class.java)
            }
            startActivity(next.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            return
        }
        // ─────────────────────────────────────────────────────────────────────────

        setContentView(R.layout.activity_login)

        dpScale = resources.displayMetrics.density
        cornerPx = 48f * dpScale

        etId = findViewById(R.id.etId)
        etPw = findViewById(R.id.etPw)
        btnLogin = findViewById(R.id.btnLogin)
        btnTogglePw = findViewById(R.id.btnTogglePw)
        tvFindId = findViewById(R.id.tvFindId)
        tvFindPw = findViewById(R.id.tvFindPw)
        tvRegister = findViewById(R.id.tvRegister)
        tvLoginError = findViewById(R.id.tvLoginError)
        pbLoading    = findViewById(R.id.pbLoading)

        // 디버그 빌드에서만 테스트 로그인 버튼 활성화
        if (isDebug) {
            val btnDebugLogin = findViewById<Button>(R.id.btnDebugLogin)
            btnDebugLogin.visibility = View.VISIBLE
            btnDebugLogin.setOnClickListener {
                // 테스트 토큰 저장
                TokenManager.saveTokens(this, "debug_token", "debug_refresh", "debug_user")
                // 온보딩 전체 플로우(튜토리얼 → 권한 → 모드 선택) 테스트를 위해 튜토리얼 표시 여부 초기화
                getSharedPreferences("app_prefs", MODE_PRIVATE)
                    .edit().remove(TutorialActivity.KEY_TUTORIAL_SHOWN).apply()
                // 항상 온보딩 첫 화면(튜토리얼)부터 시작
                startActivity(
                    TutorialActivity.intentForLogin(this).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
            }
        }

        // 입력 시 오류 상태 초기화
        val clearError = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                tvLoginError.visibility = View.GONE
                setContainerBorderNormal(etId)
                setContainerBorderNormal(etPw)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        etId.addTextChangedListener(clearError)
        etPw.addTextChangedListener(clearError)

        btnTogglePw.setOnClickListener {
            isPwVisible = !isPwVisible
            if (isPwVisible) {
                etPw.transformationMethod = HideReturnsTransformationMethod.getInstance()
                btnTogglePw.setImageResource(R.drawable.ic_eye_off)
            } else {
                etPw.transformationMethod = PasswordTransformationMethod.getInstance()
                btnTogglePw.setImageResource(R.drawable.ic_eye)
            }
            etPw.setSelection(etPw.text.length)
        }

        btnLogin.setOnClickListener {
            val id = etId.text.toString().trim()
            val pw = etPw.text.toString().trim()

            if (id.isEmpty() || pw.isEmpty()) {
                if (id.isEmpty()) setContainerBorderError(etId)
                if (pw.isEmpty()) setContainerBorderError(etPw)
                showLoginError("아이디 또는 비밀번호를 확인해주세요.")
                return@setOnClickListener
            }

            val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            viewModel.login(id, pw, deviceId)
        }

        tvFindId.setOnClickListener {
            startActivity(Intent(this, FindIdActivity::class.java))
            overridePendingTransition(R.anim.detail_enter, R.anim.detail_exit)
        }
        tvFindPw.setOnClickListener {
            startActivity(Intent(this, FindPwActivity::class.java))
            overridePendingTransition(R.anim.detail_enter, R.anim.detail_exit)
        }
        tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterStep1Activity::class.java))
            overridePendingTransition(R.anim.detail_enter, R.anim.detail_exit)
        }

        setupTermsLinks()
        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            btnLogin.isEnabled = !state.isLoading
            pbLoading.visibility = if (state.isLoading) View.VISIBLE else View.GONE
            if (state.errorMessage != null) {
                showLoginError(state.errorMessage)
            }
        }
        viewModel.loginSuccess.observe(this) { success ->
            if (success != null) {
                TokenManager.saveTokens(this, success.accessToken, success.refreshToken, success.userId)
                if (BuildConfig.DEBUG) Log.d("Login", "저장 완료 — userId=${success.userId}")
                // TODO: 백엔드 API 스펙(v4.2)상 로그인 성공 직후 Python 서버 POST /api/devices/{userId}로
                //       기기 등록(device_id=ANDROID_ID, device_name=Build.MODEL)을 호출해야 하는데
                //       현재 앱 어디에도 이 호출이 없음 — devices 컬렉션이 계속 비어있을 가능성이 있음.
                //       의도적으로 보류 중 (2026-08-17 검토, 아직 미착수).
                // 최초 로그인 시 튜토리얼 표시 (기기별 1회)
                val next = if (!TutorialActivity.isTutorialShown(this)) {
                    TutorialActivity.intentForLogin(this)
                } else {
                    Intent(this, ModeSelectActivity::class.java)
                }
                startActivity(next.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                viewModel.onLoginHandled()
            }
        }
    }

    // 이용약관·개인정보 처리방침 텍스트를 파란 밑줄 링크로 표시
    private fun setupTermsLinks() {
        val tvTerms = findViewById<TextView>(R.id.tvTerms)
        val fullText = "로그인 시 이용약관 및 개인정보 처리방침에 동의합니다."
        val spannable = SpannableString(fullText)
        val linkColor = ContextCompat.getColor(this, R.color.primary_blue)

        fun applyLink(keyword: String, url: String) {
            val start = fullText.indexOf(keyword)
            if (start < 0) return
            val end = start + keyword.length
            spannable.setSpan(ForegroundColorSpan(linkColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    if (url.isBlank()) {
                        Toast.makeText(this@LoginActivity, "약관 페이지가 아직 준비 중입니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (e: Exception) {
                            Toast.makeText(this@LoginActivity, "약관 페이지를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                override fun updateDrawState(ds: android.text.TextPaint) {
                    ds.color = linkColor
                    ds.isUnderlineText = true
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        applyLink("이용약관", TERMS_URL_SERVICE)
        applyLink("개인정보 처리방침", TERMS_URL_PRIVACY)

        tvTerms.text = spannable
        tvTerms.movementMethod = LinkMovementMethod.getInstance()
        tvTerms.highlightColor = android.graphics.Color.TRANSPARENT
    }

    companion object {
        // TODO: 약관 URL 확정 후 채우기 (RegisterStep1Activity 상수와 함께 일괄 교체)
        private const val TERMS_URL_SERVICE = ""   // 이용약관
        private const val TERMS_URL_PRIVACY = ""   // 개인정보 처리방침
    }

    private fun showLoginError(message: String) {
        tvLoginError.text = message
        tvLoginError.visibility = View.VISIBLE
    }

    // etId / etPw는 background="@null" 이므로 부모 FrameLayout에 테두리를 적용한다
    private fun setContainerBorderError(et: EditText) {
        val container = et.parent as? FrameLayout ?: return
        val drawable = GradientDrawable()
        drawable.setColor(COLOR_NORMAL)
        drawable.cornerRadius = cornerPx
        drawable.setStroke((2f * dpScale).toInt(), COLOR_RED)
        container.background = drawable
    }

    private fun setContainerBorderNormal(et: EditText) {
        val container = et.parent as? FrameLayout ?: return
        container.setBackgroundResource(R.drawable.bg_input_rounded)
    }
}
