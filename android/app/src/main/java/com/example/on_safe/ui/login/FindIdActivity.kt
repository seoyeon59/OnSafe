package com.example.on_safe.ui.login

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
import com.example.on_safe.util.EmailValidator

class FindIdActivity : AppCompatActivity() {

    private val viewModel: FindIdViewModel by viewModels()

    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etCode: EditText
    private lateinit var btnRequestCode: Button
    private lateinit var btnConfirm: Button
    private lateinit var btnBack: ImageButton
    private lateinit var btnGoLogin: Button
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
        btnBack = findViewById(R.id.btnBack)
        btnGoLogin = findViewById(R.id.btnGoLogin)
        layoutCode = findViewById(R.id.layoutCode)
        layoutResult = findViewById(R.id.layoutResult)
        tvTimer = findViewById(R.id.tvTimer)
        tvResend = findViewById(R.id.tvResend)
        tvFoundId = findViewById(R.id.tvFoundId)
        pbLoading = findViewById(R.id.pbLoading)

        btnBack.setOnClickListener { finish() }
        btnGoLogin.setOnClickListener { finish() }

        // 인증코드 발송 — 형식 검증만 여기서, 요청·상태 처리는 뷰모델 담당
        btnRequestCode.setOnClickListener {
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()

            if (name.isEmpty()) {
                Toast.makeText(this, "이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (email.isEmpty()) {
                Toast.makeText(this, "이메일을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!EmailValidator.isValid(email)) {
                Toast.makeText(this, EmailValidator.ERROR_MSG, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.requestCode(email)
        }

        // 인증코드 확인 → 아이디 조회
        btnConfirm.setOnClickListener {
            val code = etCode.text.toString().trim()
            if (code.isEmpty()) {
                Toast.makeText(this, "인증코드를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val email = etEmail.text.toString().trim()
            val name = etName.text.toString().trim()
            viewModel.confirmCode(code, email, name)
        }

        // 재전송
        tvResend.setOnClickListener {
            etCode.text.clear()
            viewModel.resendCode(etEmail.text.toString().trim())
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

            if (state.isResultVisible) {
                tvFoundId.text = state.foundId
                layoutResult.visibility = View.VISIBLE
            }
        }

        viewModel.toastMessage.observe(this) { message ->
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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
