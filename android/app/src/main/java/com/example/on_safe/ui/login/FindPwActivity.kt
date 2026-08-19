package com.example.on_safe.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.on_safe.R
import com.example.on_safe.ResetPasswordActivity

class FindPwActivity : AppCompatActivity() {

    private val viewModel: FindPwViewModel by viewModels()

    private lateinit var etUserId: EditText
    private lateinit var etEmail: EditText
    private lateinit var etCode: EditText
    private lateinit var btnRequestCode: Button
    private lateinit var btnConfirm: Button
    private lateinit var btnBack: ImageButton
    private lateinit var btnGoLogin: Button
    private lateinit var layoutCode: LinearLayout
    private lateinit var tvTimer: TextView
    private lateinit var tvResend: TextView
    private lateinit var pbLoading: ProgressBar

    // navigateToResetPassword()에서 전환(다음 화면)과 finish()(스택 정리)를 함께 호출할 때,
    // finish()의 기본 "뒤로 나가는" 전환이 방금 지정한 "다음으로 넘어가는" 전환을 덮어쓰지 않도록 함
    private var suppressFinishTransition = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_find_pw)

        etUserId = findViewById(R.id.etUserId)
        etEmail = findViewById(R.id.etEmail)
        etCode = findViewById(R.id.etCode)
        btnRequestCode = findViewById(R.id.btnRequestCode)
        btnConfirm = findViewById(R.id.btnConfirm)
        btnBack = findViewById(R.id.btnBack)
        btnGoLogin = findViewById(R.id.btnGoLogin)
        layoutCode = findViewById(R.id.layoutCode)
        tvTimer = findViewById(R.id.tvTimer)
        tvResend  = findViewById(R.id.tvResend)
        pbLoading = findViewById(R.id.pbLoading)

        btnBack.setOnClickListener { finish() }
        btnGoLogin.setOnClickListener { finish() }

        // 재설정 코드 발송 — 입력값 형식 검증만 여기서, 실제 요청/상태 처리는 뷰모델에 맡김
        btnRequestCode.setOnClickListener {
            val userId = etUserId.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

            if (userId.isEmpty()) {
                Toast.makeText(this, "아이디를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (email.isEmpty()) {
                Toast.makeText(this, "이메일을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!emailRegex.matches(email)) {
                Toast.makeText(this, "올바른 이메일 형식을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.requestCode(userId, email)
        }

        // 재설정 코드 확인
        btnConfirm.setOnClickListener {
            val code = etCode.text.toString().trim()
            if (code.isEmpty()) {
                Toast.makeText(this, "재설정 코드를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.confirmCode(etUserId.text.toString().trim(), code)
        }

        // 재전송
        tvResend.setOnClickListener {
            etCode.text.clear()
            viewModel.resendCode(etUserId.text.toString().trim(), etEmail.text.toString().trim())
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            pbLoading.visibility = if (state.isLoading) View.VISIBLE else View.GONE

            btnRequestCode.isEnabled = state.isRequestCodeEnabled
            btnRequestCode.alpha = if (state.isRequestCodeEnabled) 1.0f else 0.4f

            layoutCode.visibility = if (state.isCodeLayoutVisible) View.VISIBLE else View.GONE
            tvTimer.visibility = if (state.isCodeLayoutVisible) View.VISIBLE else View.GONE
            tvTimer.text = state.timerText

            tvResend.visibility = if (state.isResendVisible) View.VISIBLE else View.GONE

            btnConfirm.isEnabled = state.isConfirmEnabled
            btnConfirm.alpha = if (state.isConfirmEnabled) 1.0f else 0.4f
        }

        viewModel.toastMessage.observe(this) { message ->
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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
        val intent = Intent(this, ResetPasswordActivity::class.java)
        intent.putExtra("userId", etUserId.text.toString().trim())
        intent.putExtra("mode", ResetPasswordActivity.MODE_FIND_PW)
        startActivity(intent)
        overridePendingTransition(R.anim.detail_enter, R.anim.detail_exit)
        suppressFinishTransition = true
        finish()
    }

    // 좌상단 뒤로가기 버튼이 있는 화면 공통 — 알림 화면과 동일한 "파고들어왔다 빠져나가는" 전환
    // (다음 화면으로 넘어가며 스택 정리 차원에서 finish()를 호출하는 경우는 예외)
    override fun finish() {
        super.finish()
        if (!suppressFinishTransition) {
            overridePendingTransition(R.anim.detail_pop_enter, R.anim.detail_pop_exit)
        }
    }
}
