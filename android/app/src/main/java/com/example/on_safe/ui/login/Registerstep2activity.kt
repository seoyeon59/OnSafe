package com.example.on_safe.ui.login

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
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
import com.example.on_safe.FieldValidation
import com.example.on_safe.R
import com.example.on_safe.util.PhoneField
import com.example.on_safe.util.onTextChanged

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
    private lateinit var btnTogglePw: ImageButton
    private lateinit var btnTogglePwConfirm: ImageButton
    private lateinit var btnComplete: Button
    private lateinit var btnBack: ImageButton
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

    private var isPwVisible = false
    private var isPwConfirmVisible = false
    private var isFormattingPhone = false

    // 완료 조건 충족 여부 — 버튼은 항상 활성, 미충족 시 안내 토스트
    private var isCompleteReady = false

    // Step1에서 전달된 마케팅 수신 동의 (미전달 대비 기본 false)
    private var marketingConsent = false

    private val COLOR_RED = 0xFFEF4444.toInt()
    private val COLOR_GREEN = 0xFF22C55E.toInt()
    private val COLOR_NORMAL = 0xFFF4F7FB.toInt()

    private var dpScale = 0f
    private var cornerPx = 0f

    // 주소 검색 결과 수신 런처
    private val addressLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val address = result.data?.getStringExtra(AddressSearchActivity.EXTRA_ADDRESS) ?: ""
            val zipNo   = result.data?.getStringExtra(AddressSearchActivity.EXTRA_ZIP) ?: ""
            val displayed = if (zipNo.isNotEmpty()) "$address ($zipNo)" else address
            etAddress.setText(displayed)
            viewModel.onAddressChanged(displayed)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_step2)

        dpScale = resources.displayMetrics.density
        cornerPx = 48f * dpScale

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
        btnTogglePw = findViewById(R.id.btnTogglePw)
        btnTogglePwConfirm = findViewById(R.id.btnTogglePwConfirm)
        btnComplete = findViewById(R.id.btnComplete)
        btnBack     = findViewById(R.id.btnBack)
        pbLoading   = findViewById(R.id.pbLoading)
        layoutEmailCode = findViewById(R.id.layoutEmailCode)
        tvIdMessage = findViewById(R.id.tvIdMessage)
        tvPwMessage = findViewById(R.id.tvPwMessage)
        tvPwConfirmMessage = findViewById(R.id.tvPwConfirmMessage)
        tvPhoneMessage = findViewById(R.id.tvPhoneMessage)
        tvEmailMessage = findViewById(R.id.tvEmailMessage)
        tvEmailVerified = findViewById(R.id.tvEmailVerified)
        tvEmailTimer = findViewById(R.id.tvEmailTimer)
        tvEmailResend = findViewById(R.id.tvEmailResend)

        btnBack.setOnClickListener { finish() }

        btnTogglePw.setOnClickListener {
            isPwVisible = !isPwVisible
            etPw.transformationMethod = if (isPwVisible)
                HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
            etPw.setSelection(etPw.text.length)
            btnTogglePw.setImageResource(if (isPwVisible) R.drawable.ic_eye_off else R.drawable.ic_eye)
        }

        btnTogglePwConfirm.setOnClickListener {
            isPwConfirmVisible = !isPwConfirmVisible
            etPwConfirm.transformationMethod = if (isPwConfirmVisible)
                HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
            etPwConfirm.setSelection(etPwConfirm.text.length)
            btnTogglePwConfirm.setImageResource(if (isPwConfirmVisible) R.drawable.ic_eye_off else R.drawable.ic_eye)
        }

        // 유효성 판단은 전부 뷰모델 담당 — Activity는 입력 전달만
        // (etAddressDetail은 선택 항목이라 watcher 불필요)
        etId.onTextChanged(viewModel::onIdChanged)
        etPw.onTextChanged(viewModel::onPwChanged)
        etPwConfirm.onTextChanged(viewModel::onPwConfirmChanged)
        etEmail.onTextChanged(viewModel::onEmailChanged)
        etName.onTextChanged(viewModel::onNameChanged)

        // 전화번호만 예외 — 하이픈 자동 포맷이 View 조작이라 여기서 처리
        etPhone.onTextChanged { raw ->
            if (!isFormattingPhone) {
                isFormattingPhone = true
                val formatted = PhoneField.formatInput(raw)
                if (formatted != raw) {
                    etPhone.setText(formatted)
                    etPhone.setSelection(formatted.length)   // 커서 맨 뒤로
                }
                isFormattingPhone = false
            }
            viewModel.onPhoneChanged(etPhone.text.toString())
        }

        // 아이디 중복 확인 — 형식 검증은 뷰모델, 빈 값 체크만 여기서
        btnCheckId.setOnClickListener {
            val id = etId.text.toString().trim()
            if (id.isEmpty()) {
                Toast.makeText(this, "아이디를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.checkId(id)
        }

        // 이메일 인증 요청
        btnVerifyEmail.setOnClickListener {
            viewModel.verifyEmail()
        }

        // 인증코드 확인
        btnConfirmCode.setOnClickListener {
            val code = etEmailCode.text.toString().trim()
            if (code.isEmpty()) {
                Toast.makeText(this, "인증코드를 입력해주세요.", Toast.LENGTH_SHORT).show()
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
            // 조회 중에도 활성처럼 보이던 문제 — isEnabled와 알파 조건 일치
            btnCheckId.isEnabled = state.isIdCheckEnabled
            btnCheckId.alpha = if (state.isIdCheckEnabled) 1.0f else 0.4f
            applyValidation(etId, tvIdMessage, state.idValidation)

            // 비밀번호 / 비밀번호 확인
            applyValidation(etPw, tvPwMessage, state.pwValidation)
            applyValidation(etPwConfirm, tvPwConfirmMessage, state.pwConfirmValidation)

            // 전화번호
            applyValidation(etPhone, tvPhoneMessage, state.phoneValidation)

            // 이메일
            applyValidation(etEmail, tvEmailMessage, state.emailValidation)
            btnVerifyEmail.isEnabled = state.isEmailVerifyEnabled
            btnVerifyEmail.alpha = if (state.isEmailVerifyEnabled) 1.0f else 0.4f
            layoutEmailCode.visibility = if (state.isEmailCodeLayoutVisible) View.VISIBLE else View.GONE
            tvEmailTimer.text = state.emailTimerText
            tvEmailResend.visibility = if (state.isEmailResendVisible) View.VISIBLE else View.GONE
            tvEmailVerified.visibility = if (state.isEmailVerified) View.VISIBLE else View.GONE
            btnConfirmCode.isEnabled = state.isConfirmCodeEnabled
            btnConfirmCode.alpha = if (state.isConfirmCodeEnabled) 1.0f else 0.4f

            // 최종 가입 — 안내 표시를 위해 버튼은 활성 유지, 흐리게만 처리
            pbLoading.visibility = if (state.isLoading) View.VISIBLE else View.GONE
            isCompleteReady = state.isCompleteEnabled
            btnComplete.isEnabled = !state.isLoading
            btnComplete.alpha = if (state.isCompleteEnabled) 1.0f else 0.4f
        }

        viewModel.toastMessage.observe(this) { message ->
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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
                tv.visibility = View.GONE
                setInputBorderNormal(et)
            }
            is FieldValidation.Valid -> {
                showMessage(tv, validation.message, COLOR_GREEN)
                setInputBorderColor(et, COLOR_GREEN)
            }
            is FieldValidation.Invalid -> {
                showMessage(tv, validation.message, COLOR_RED)
                setInputBorderColor(et, COLOR_RED)
            }
        }
    }

    private fun showMessage(tv: TextView, msg: String, color: Int) {
        tv.text = msg
        tv.setTextColor(color)
        tv.visibility = View.VISIBLE
    }

    private fun setInputBorderColor(et: EditText, color: Int) {
        val drawable = GradientDrawable()
        drawable.setColor(COLOR_NORMAL)
        drawable.cornerRadius = cornerPx
        drawable.setStroke((2f * dpScale).toInt(), color)
        et.background = drawable
    }

    private fun setInputBorderNormal(et: EditText) {
        et.setBackgroundResource(R.drawable.bg_input_rounded)
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
