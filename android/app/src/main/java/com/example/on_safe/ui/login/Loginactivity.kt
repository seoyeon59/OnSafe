package com.example.on_safe.ui.login

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
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
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import android.widget.Toast
import com.example.on_safe.BuildConfig
import com.example.on_safe.R
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.isOk
import com.example.on_safe.ui.tutorial.TutorialActivity
import com.example.on_safe.util.DoubleBackToExit
import com.example.on_safe.util.INPUT_BORDER_ERROR
import com.example.on_safe.util.TermsLinks
import com.example.on_safe.util.TokenManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.example.on_safe.util.bindPasswordToggle
import com.example.on_safe.util.clearInputBorder
import com.example.on_safe.util.onTextChanged
import com.example.on_safe.util.openTermsUrl
import com.example.on_safe.util.setInputBorder

class LoginActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels()

    private lateinit var etId: EditText
    private lateinit var etPw: EditText
    private lateinit var btnLogin: Button
    private lateinit var tvLoginError: TextView
    private lateinit var pbLoading: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        pbLoading = findViewById(R.id.pbLoading)

        // 자동 로그인 — 로컬 토큰 유효 + 30일 이내이면 서버 세션 검증 후 진입.
        // 로컬 만료만 보면 회원탈퇴/강제로그아웃 뒤에도 진입 가능하므로 서버 블랙리스트까지 확인한다.
        if (TokenManager.isLoggedIn(this) && !TokenManager.isSessionExpired(this)) {
            pbLoading.isVisible = true
            tryAutoLogin()
            return
        }

        setupLoginForm()
    }

    // 로그인 폼 초기화 — 신규 진입/자동 로그인 실패 양쪽에서 호출.
    private fun setupLoginForm() {
        pbLoading.isVisible = false

        etId = findViewById(R.id.etId)
        etPw = findViewById(R.id.etPw)
        btnLogin = findViewById(R.id.btnLogin)
        tvLoginError = findViewById(R.id.tvLoginError)

        if (BuildConfig.DEBUG) setupDebugLogin()

        // 입력 시 오류 상태 초기화
        val clearError = {
            tvLoginError.isVisible = false
            inputContainer(etId)?.clearInputBorder()
            inputContainer(etPw)?.clearInputBorder()
        }
        etId.onTextChanged { clearError() }
        etPw.onTextChanged { clearError() }

        findViewById<ImageButton>(R.id.btnTogglePw).bindPasswordToggle(etPw)

        btnLogin.setOnClickListener {
            val id = etId.text.toString().trim()
            val pw = etPw.text.toString().trim()

            if (id.isEmpty() || pw.isEmpty()) {
                if (id.isEmpty()) inputContainer(etId)?.setInputBorder(INPUT_BORDER_ERROR)
                if (pw.isEmpty()) inputContainer(etPw)?.setInputBorder(INPUT_BORDER_ERROR)
                showLoginError("아이디 또는 비밀번호를 확인해주세요.")
                return@setOnClickListener
            }

            val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            viewModel.login(id, pw, deviceId)
        }

        findViewById<TextView>(R.id.tvFindId).setOnClickListener { openScreen(FindIdActivity::class.java) }
        findViewById<TextView>(R.id.tvFindPw).setOnClickListener { openScreen(FindPwActivity::class.java) }
        findViewById<TextView>(R.id.tvRegister).setOnClickListener { openScreen(RegisterStep1Activity::class.java) }

        setupTermsLinks()
        observeViewModel()

        // 로그인 화면은 앱의 시작점 — 실수 종료 방지용 뒤로가기 2회
        DoubleBackToExit.attach(this)
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            btnLogin.isEnabled = !state.isLoading
            pbLoading.isVisible = state.isLoading
            if (state.errorMessage != null) {
                showLoginError(state.errorMessage)
                viewModel.onErrorShown()
            }
        }
        viewModel.loginSuccess.observe(this) { success ->
            if (success == null) return@observe
            TokenManager.saveTokens(this, success.accessToken, success.refreshToken, success.userId)
            if (BuildConfig.DEBUG) Log.d("Login", "저장 완료 — userId=${success.userId}")
            // 기기 등록은 카메라 모드 진입 시 수행 — 여기서 하면 보호자 폰이
            // 자기 자신을 카메라로 등록하게 됨 (CameraModeViewModel.registerDevice)
            startOnboarding()
            viewModel.onLoginHandled()
        }
    }

    // 자동 로그인 진입 전 서버 세션 검증. 200 이면 온보딩으로, 401/네트워크 오류면 로컬 세션 정리 후 로그인 폼 노출.
    private fun tryAutoLogin() {
        val accessToken = TokenManager.getAccessToken(this)
        if (accessToken.isNullOrBlank()) {
            setupLoginForm()
            return
        }
        lifecycleScope.launch {
            val ok = try {
                val response = ApiClient.api.validateToken("Bearer $accessToken")
                response.isOk
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (ok) {
                startOnboarding()
            } else {
                TokenManager.clearSession(this@LoginActivity)
                Toast.makeText(this@LoginActivity, "세션이 만료되어 다시 로그인해주세요.", Toast.LENGTH_SHORT).show()
                setupLoginForm()
            }
        }
    }

    // 튜토리얼 미시청이면 튜토리얼부터, 아니면 모드 선택으로 (기기별 1회)
    private fun startOnboarding() {
        val next = if (TutorialActivity.isTutorialShown(this)) {
            Intent(this, ModeSelectActivity::class.java)
        } else {
            TutorialActivity.intentForLogin(this)
        }
        startActivity(next.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }

    // 디버그 빌드 전용 테스트 로그인 — 온보딩 전체 플로우 확인용
    private fun setupDebugLogin() {
        val btnDebugLogin = findViewById<Button>(R.id.btnDebugLogin)
        btnDebugLogin.isVisible = true
        btnDebugLogin.setOnClickListener {
            TokenManager.saveTokens(this, "debug_token", "debug_refresh", "debug_user")
            // 튜토리얼 표시 여부 초기화 — 항상 온보딩 첫 화면부터 시작
            TutorialActivity.resetShownFlag(this)
            startActivity(
                TutorialActivity.intentForLogin(this).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
        }
    }

    private fun openScreen(target: Class<*>) {
        startActivity(Intent(this, target))
        overridePendingTransition(R.anim.detail_enter, R.anim.detail_exit)
    }

    // 이용약관·개인정보 처리방침을 파란 밑줄 링크로 표시
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
                override fun onClick(widget: View) = openTermsUrl(url)

                override fun updateDrawState(ds: TextPaint) {
                    ds.color = linkColor
                    ds.isUnderlineText = true
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        applyLink("이용약관", TermsLinks.SERVICE)
        applyLink("개인정보 처리방침", TermsLinks.PRIVACY)

        tvTerms.text = spannable
        tvTerms.movementMethod = LinkMovementMethod.getInstance()
        tvTerms.highlightColor = android.graphics.Color.TRANSPARENT
    }

    private fun showLoginError(message: String) {
        tvLoginError.text = message
        tvLoginError.isVisible = true
    }

    // etId / etPw는 background="@null" — 테두리는 부모 FrameLayout에 적용
    private fun inputContainer(et: EditText): FrameLayout? = et.parent as? FrameLayout
}
