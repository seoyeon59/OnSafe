package com.example.on_safe.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
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
    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var btnVerify: Button
    private lateinit var pbLoading: ProgressBar

    // 다음 화면 전환과 finish()를 함께 호출할 때, finish()의 역방향 전환이
    // 방금 지정한 정방향 전환을 덮어쓰는 것을 방지
    private var suppressFinishTransition = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_find_pw)

        etUserId = findViewById(R.id.etUserId)
        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        btnVerify = findViewById(R.id.btnVerify)
        pbLoading = findViewById(R.id.pbLoading)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnGoLogin).setOnClickListener { finish() }

        // 본인확인 — 입력 검증만 여기서, 요청·상태 처리는 뷰모델 담당
        btnVerify.setOnClickListener {
            val userId = etUserId.text.toString().trim()
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val error = when {
                userId.isEmpty() -> "아이디를 입력해주세요."
                name.isEmpty() -> "이름을 입력해주세요."
                email.isEmpty() -> "이메일을 입력해주세요."
                !EmailValidator.isValid(email) -> EmailValidator.ERROR_MSG
                else -> null
            }
            if (error != null) {
                toast(error)
                return@setOnClickListener
            }
            viewModel.verifyIdentity(userId, name, email)
        }

        // 키보드 완료 키로도 제출 — false 반환으로 키보드는 기본대로 닫힘
        etEmail.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) btnVerify.performClick()
            false
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            pbLoading.isVisible = state.isLoading
            btnVerify.setEnabledWithAlpha(state.isVerifyEnabled)
        }

        viewModel.toastMessage.observe(this) { message ->
            if (message != null) {
                toast(message)
                viewModel.onToastShown()
            }
        }

        viewModel.navigateToReset.observe(this) { target ->
            if (target != null) {
                navigateToResetPassword(target)
                viewModel.onNavigated()
            }
        }
    }

    private fun navigateToResetPassword(target: ResetTarget) {
        startActivity(
            Intent(this, ResetPasswordActivity::class.java).apply {
                putExtra(ResetPasswordActivity.EXTRA_USER_ID, target.userId)
                putExtra(ResetPasswordActivity.EXTRA_RESET_TICKET, target.resetTicket)
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
