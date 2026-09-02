package com.example.on_safe.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.on_safe.R
import com.example.on_safe.ResetPasswordActivity
import com.example.on_safe.util.EmailValidator
import com.example.on_safe.util.setEnabledWithAlpha
import com.example.on_safe.util.toast

class FindPwActivity : AppCompatActivity() {

    private val viewModel: FindPwViewModel by viewModels()

    private lateinit var etUserId: EditText
    private lateinit var etEmail: EditText
    private lateinit var etCode: EditText
    private lateinit var btnRequestCode: Button
    private lateinit var btnConfirm: Button
    private lateinit var layoutCode: LinearLayout
    private lateinit var tvTimer: TextView
    private lateinit var tvResend: TextView
    private lateinit var pbLoading: ProgressBar

    // 다음 화면 전환과 finish()를 함께 호출할 때, finish()의 역방향 전환이
    // 방금 지정한 정방향 전환을 덮어쓰는 것을 방지
    private var suppressFinishTransition = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_find_pw)

        etUserId = findViewById(R.id.etUserId)
        etEmail = findViewById(R.id.etEmail)
        etCode = findViewById(R.id.etCode)
        btnRequestCode = findViewById(R.id.btnRequestCode)
        btnConfirm = findViewById(R.id.btnConfirm)
        layoutCode = findViewById(R.id.layoutCode)
        tvTimer = findViewById(R.id.tvTimer)
        tvResend = findViewById(R.id.tvResend)
        pbLoading = findViewById(R.id.pbLoading)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnGoLogin).setOnClickListener { finish() }

        // 재설정 코드 발송 — 입력 검증만 여기서, 요청·상태 처리는 뷰모델 담당
        btnRequestCode.setOnClickListener {
            val userId = etUserId.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val error = when {
                userId.isEmpty() -> "아이디를 입력해주세요."
                email.isEmpty() -> "이메일을 입력해주세요."
                !EmailValidator.isValid(email) -> EmailValidator.ERROR_MSG
                else -> null
            }
            if (error != null) {
                toast(error)
                return@setOnClickListener
            }
            viewModel.requestCode(userId, email)
        }

        // 재설정 코드 확인
        btnConfirm.setOnClickListener {
            val code = etCode.text.toString().trim()
            if (code.isEmpty()) {
                toast("재설정 코드를 입력해주세요.")
                return@setOnClickListener
            }
            viewModel.confirmCode(etUserId.text.toString().trim(), code)
        }

        tvResend.setOnClickListener {
            etCode.text.clear()
            viewModel.resendCode(etUserId.text.toString().trim(), etEmail.text.toString().trim())
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            pbLoading.isVisible = state.isLoading
            btnRequestCode.setEnabledWithAlpha(state.isRequestCodeEnabled)
            btnConfirm.setEnabledWithAlpha(state.isConfirmEnabled)

            layoutCode.isVisible = state.isCodeLayoutVisible
            tvTimer.isVisible = state.isCodeLayoutVisible
            tvTimer.text = state.timerText
            tvResend.isVisible = state.isResendVisible
        }

        viewModel.toastMessage.observe(this) { message ->
            if (message != null) {
                toast(message)
                viewModel.onToastShown()
            }
        }

        viewModel.navigateToReset.observe(this) { shouldNavigate ->
            if (shouldNavigate) {
                navigateToResetPassword()
                viewModel.onNavigated()
            }
        }
    }

    private fun navigateToResetPassword() {
        startActivity(
            Intent(this, ResetPasswordActivity::class.java).apply {
                putExtra(ResetPasswordActivity.EXTRA_USER_ID, etUserId.text.toString().trim())
                putExtra(ResetPasswordActivity.EXTRA_MODE, ResetPasswordActivity.MODE_FIND_PW)
            }
        )
        overridePendingTransition(R.anim.detail_enter, R.anim.detail_exit)
        suppressFinishTransition = true
        finish()
    }

    // 좌상단 뒤로가기 화면 공통 전환 — 알림 화면과 동일
    // (다음 화면으로 넘어가며 스택을 정리하는 finish()는 예외)
    override fun finish() {
        super.finish()
        if (!suppressFinishTransition) {
            overridePendingTransition(R.anim.detail_pop_enter, R.anim.detail_pop_exit)
        }
    }
}
