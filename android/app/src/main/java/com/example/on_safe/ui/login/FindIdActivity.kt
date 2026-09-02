package com.example.on_safe.ui.login

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
import com.example.on_safe.util.EmailValidator
import com.example.on_safe.util.setEnabledWithAlpha
import com.example.on_safe.util.toast

class FindIdActivity : AppCompatActivity() {

    private val viewModel: FindIdViewModel by viewModels()

    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etCode: EditText
    private lateinit var btnRequestCode: Button
    private lateinit var btnConfirm: Button
    private lateinit var layoutCode: LinearLayout
    private lateinit var layoutResult: LinearLayout
    private lateinit var tvTimer: TextView
    private lateinit var tvResend: TextView
    private lateinit var tvFoundId: TextView
    private lateinit var pbLoading: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_find_id)

        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        etCode = findViewById(R.id.etCode)
        btnRequestCode = findViewById(R.id.btnRequestCode)
        btnConfirm = findViewById(R.id.btnConfirm)
        layoutCode = findViewById(R.id.layoutCode)
        layoutResult = findViewById(R.id.layoutResult)
        tvTimer = findViewById(R.id.tvTimer)
        tvResend = findViewById(R.id.tvResend)
        tvFoundId = findViewById(R.id.tvFoundId)
        pbLoading = findViewById(R.id.pbLoading)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnGoLogin).setOnClickListener { finish() }

        // 인증코드 발송 — 입력 검증만 여기서, 요청·상태 처리는 뷰모델 담당
        btnRequestCode.setOnClickListener {
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val error = when {
                name.isEmpty() -> "이름을 입력해주세요."
                email.isEmpty() -> "이메일을 입력해주세요."
                !EmailValidator.isValid(email) -> EmailValidator.ERROR_MSG
                else -> null
            }
            if (error != null) {
                toast(error)
                return@setOnClickListener
            }
            viewModel.requestCode(email)
        }

        // 인증코드 확인 → 아이디 조회
        btnConfirm.setOnClickListener {
            val code = etCode.text.toString().trim()
            if (code.isEmpty()) {
                toast("인증코드를 입력해주세요.")
                return@setOnClickListener
            }
            viewModel.confirmCode(code, etEmail.text.toString().trim(), etName.text.toString().trim())
        }

        tvResend.setOnClickListener {
            etCode.text.clear()
            viewModel.resendCode(etEmail.text.toString().trim())
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

            if (state.isResultVisible) {
                tvFoundId.text = state.foundId
                layoutResult.isVisible = true
            }
        }

        viewModel.toastMessage.observe(this) { message ->
            if (message != null) {
                toast(message)
                viewModel.onToastShown()
            }
        }
    }

    // 좌상단 뒤로가기 화면 공통 전환 — 알림 화면과 동일
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.detail_pop_enter, R.anim.detail_pop_exit)
    }
}
