package com.example.on_safe.ui.login

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.on_safe.R
import com.example.on_safe.util.FieldValidation
import com.example.on_safe.util.INPUT_BORDER_ERROR
import com.example.on_safe.util.INPUT_BORDER_VALID
import com.example.on_safe.util.bindPasswordToggle
import com.example.on_safe.util.bindPhoneFormatting
import com.example.on_safe.util.clearInputBorder
import com.example.on_safe.util.onTextChanged
import com.example.on_safe.util.setEnabledWithAlpha
import com.example.on_safe.util.setInputBorder
import com.example.on_safe.util.toast

class RegisterStep2Activity : AppCompatActivity() {

    private val viewModel: RegisterStep2ViewModel by viewModels()

    private lateinit var etId: EditText
    private lateinit var etPw: EditText
    private lateinit var etPwConfirm: EditText
    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etEmail: EditText
    private lateinit var etEmailCode: EditText
    private lateinit var etAddress: EditText
    private lateinit var etAddressDetail: EditText

    private lateinit var btnCheckId: Button
    private lateinit var btnVerifyEmail: Button
    private lateinit var btnConfirmCode: Button
    private lateinit var btnComplete: Button
    private lateinit var pbLoading: ProgressBar

    private lateinit var layoutEmailCode: LinearLayout
    private lateinit var tvIdMessage: TextView
    private lateinit var tvPwMessage: TextView
    private lateinit var tvPwConfirmMessage: TextView
    private lateinit var tvPhoneMessage: TextView
    private lateinit var tvEmailMessage: TextView
    private lateinit var tvEmailVerified: TextView
    private lateinit var tvEmailTimer: TextView
    private lateinit var tvEmailResend: TextView

    // 완료 조건 충족 여부 — 버튼은 항상 활성, 미충족 시 안내 토스트
    private var isCompleteReady = false

    // Step1에서 전달된 마케팅 수신 동의 (미전달 대비 기본 false)
    private var marketingConsent = false

    // 주소 검색 결과 수신 런처
    private val addressLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val address = result.data?.getStringExtra(AddressSearchActivity.EXTRA_ADDRESS) ?: ""
            val zipNo = result.data?.getStringExtra(AddressSearchActivity.EXTRA_ZIP) ?: ""
            val displayed = if (zipNo.isNotEmpty()) "$address ($zipNo)" else address
            etAddress.setText(displayed)
            viewModel.onAddressChanged(displayed)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_step2)

        marketingConsent = intent.getBooleanExtra(EXTRA_MARKETING_CONSENT, false)

        etId = findViewById(R.id.etId)
        etPw = findViewById(R.id.etPw)
        etPwConfirm = findViewById(R.id.etPwConfirm)
        etName = findViewById(R.id.etName)
        etPhone = findViewById(R.id.etPhone)
        etEmail = findViewById(R.id.etEmail)
        etEmailCode = findViewById(R.id.etEmailCode)
        etAddress = findViewById(R.id.etAddress)
        etAddressDetail = findViewById(R.id.etAddressDetail)
        btnCheckId = findViewById(R.id.btnCheckId)
        btnVerifyEmail = findViewById(R.id.btnVerifyEmail)
        btnConfirmCode = findViewById(R.id.btnConfirmCode)
        btnComplete = findViewById(R.id.btnComplete)
        pbLoading = findViewById(R.id.pbLoading)
        layoutEmailCode = findViewById(R.id.layoutEmailCode)
        tvIdMessage = findViewById(R.id.tvIdMessage)
        tvPwMessage = findViewById(R.id.tvPwMessage)
        tvPwConfirmMessage = findViewById(R.id.tvPwConfirmMessage)
        tvPhoneMessage = findViewById(R.id.tvPhoneMessage)
        tvEmailMessage = findViewById(R.id.tvEmailMessage)
        tvEmailVerified = findViewById(R.id.tvEmailVerified)
        tvEmailTimer = findViewById(R.id.tvEmailTimer)
        tvEmailResend = findViewById(R.id.tvEmailResend)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnTogglePw).bindPasswordToggle(etPw)
        findViewById<ImageButton>(R.id.btnTogglePwConfirm).bindPasswordToggle(etPwConfirm)

        // 유효성 판단은 전부 뷰모델 담당 — Activity는 입력 전달만
        // (etAddressDetail은 선택 항목이라 watcher 불필요)
        etId.onTextChanged(viewModel::onIdChanged)
        etPw.onTextChanged(viewModel::onPwChanged)
        etPwConfirm.onTextChanged(viewModel::onPwConfirmChanged)
        etEmail.onTextChanged(viewModel::onEmailChanged)
        etName.onTextChanged(viewModel::onNameChanged)
        // 전화번호만 예외 — 하이픈 자동 포맷이 View 조작이라 별도 바인딩
        etPhone.bindPhoneFormatting(viewModel::onPhoneChanged)

        // 아이디 중복 확인 — 형식 검증은 뷰모델, 빈 값 체크만 여기서
        btnCheckId.setOnClickListener {
            val id = etId.text.toString().trim()
            if (id.isEmpty()) {
                toast("아이디를 입력해주세요.")
                return@setOnClickListener
            }
            viewModel.checkId(id)
        }

        btnVerifyEmail.setOnClickListener { viewModel.verifyEmail() }

        btnConfirmCode.setOnClickListener {
            val code = etEmailCode.text.toString().trim()
            if (code.isEmpty()) {
                toast("인증코드를 입력해주세요.")
                return@setOnClickListener
            }
            viewModel.confirmEmailCode(code)
        }

        // 재전송 — 중복 탭 방지용 즉시 숨김, 실패 시에만 복구
        tvEmailResend.setOnClickListener {
            etEmailCode.text.clear()
            viewModel.resendEmailCode()
        }

        // 도로명 주소 API 연결
        etAddress.setOnClickListener {
            addressLauncher.launch(Intent(this, AddressSearchActivity::class.java))
            overridePendingTransition(R.anim.detail_enter, R.anim.detail_exit)
        }

        btnComplete.setOnClickListener {
            // 조건 미충족 시 차단 대신 누락 항목 안내
            if (!isCompleteReady) {
                viewModel.showFirstMissingRequirement()
                return@setOnClickListener
            }
            viewModel.register(
                password = etPw.text.toString(),
                phone = etPhone.text.toString(),
                addressDetail = etAddressDetail.text.toString(),
                marketingConsent = marketingConsent
            )
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            // 아이디
            btnCheckId.setEnabledWithAlpha(state.isIdCheckEnabled)
            applyValidation(etId, tvIdMessage, state.idValidation)

            // 비밀번호 / 비밀번호 확인
            applyValidation(etPw, tvPwMessage, state.pwValidation)
            applyValidation(etPwConfirm, tvPwConfirmMessage, state.pwConfirmValidation)

            // 전화번호
            applyValidation(etPhone, tvPhoneMessage, state.phoneValidation)

            // 이메일
            applyValidation(etEmail, tvEmailMessage, state.emailValidation)
            btnVerifyEmail.setEnabledWithAlpha(state.isEmailVerifyEnabled)
            btnConfirmCode.setEnabledWithAlpha(state.isConfirmCodeEnabled)
            layoutEmailCode.isVisible = state.isEmailCodeLayoutVisible
            tvEmailTimer.text = state.emailTimerText
            tvEmailResend.isVisible = state.isEmailResendVisible
            tvEmailVerified.isVisible = state.isEmailVerified

            // 최종 가입 — 안내 표시를 위해 버튼은 활성 유지, 흐리게만 처리
            pbLoading.isVisible = state.isLoading
            isCompleteReady = state.isCompleteEnabled
            btnComplete.isEnabled = !state.isLoading
            btnComplete.alpha = if (state.isCompleteEnabled) 1.0f else 0.4f
        }

        viewModel.toastMessage.observe(this) { message ->
            if (message != null) {
                toast(message)
                viewModel.onToastShown()
            }
        }

        viewModel.registerSuccess.observe(this) { success ->
            if (success) {
                viewModel.onRegisterHandled()
                startActivity(
                    Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
            }
        }
    }

    private fun applyValidation(et: EditText, tv: TextView, validation: FieldValidation) {
        when (validation) {
            is FieldValidation.Empty -> {
                tv.isVisible = false
                et.clearInputBorder()
            }
            is FieldValidation.Valid -> showMessage(et, tv, validation.message, INPUT_BORDER_VALID)
            is FieldValidation.Invalid -> showMessage(et, tv, validation.message, INPUT_BORDER_ERROR)
        }
    }

    private fun showMessage(et: EditText, tv: TextView, msg: String, color: Int) {
        tv.text = msg
        tv.setTextColor(color)
        tv.isVisible = true
        et.setInputBorder(color)
    }

    // 좌상단 뒤로가기 화면 공통 전환 — 알림 화면과 동일
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.detail_pop_enter, R.anim.detail_pop_exit)
    }

    companion object {
        const val EXTRA_MARKETING_CONSENT = "extra_marketing_consent"
    }
}
