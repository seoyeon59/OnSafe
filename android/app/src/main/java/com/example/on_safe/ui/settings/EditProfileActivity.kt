package com.example.on_safe.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isVisible
import com.example.on_safe.R
import com.example.on_safe.network.dto.UserResponse
import com.example.on_safe.ui.login.AddressSearchActivity
import com.example.on_safe.util.EmailValidator
import com.example.on_safe.util.FieldValidation
import com.example.on_safe.util.INPUT_BORDER_ERROR
import com.example.on_safe.util.INPUT_BORDER_VALID
import com.example.on_safe.util.PhoneField
import com.example.on_safe.util.TokenManager
import com.example.on_safe.util.bindPhoneFormatting
import com.example.on_safe.util.clearInputBorder
import com.example.on_safe.util.onTextChanged
import com.example.on_safe.util.setInputBorder
import com.example.on_safe.util.toast

// 개인정보 수정 화면 — 진입 시 비밀번호 확인 후 폼 활성화
class EditProfileActivity : AppCompatActivity() {

    private val viewModel: EditProfileViewModel by viewModels()

    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etEmail: EditText
    private lateinit var etAddress1: EditText
    private lateinit var etAddress2: EditText
    private lateinit var btnSave: Button
    private lateinit var btnBack: ImageButton
    private lateinit var switchMarketing: SwitchCompat

    // 인증 전에는 숨김 처리
    private lateinit var formContainer: View

    // 서버 값으로 스위치를 세팅할 때 리스너 재발화(재-PUT) 방지
    private var suppressMarketingListener = false

    // 폼 자동 채움 중 검증 문구 억제 — 진입 즉시 전 칸이 초록으로 바뀌면
    // 사용자가 직접 입력한 것으로 오해
    private var isFillingForm = false

    private lateinit var tvNameMessage: TextView
    private lateinit var tvPhoneMessage: TextView
    private lateinit var tvEmailMessage: TextView

    private val settingsPrefs by lazy {
        getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
    }

    // 주소 검색 결과 수신 런처
    private val addressLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val address = result.data?.getStringExtra(AddressSearchActivity.EXTRA_ADDRESS) ?: ""
            val zipNo   = result.data?.getStringExtra(AddressSearchActivity.EXTRA_ZIP) ?: ""
            // 도로명주소 + 우편번호 자동 입력
            etAddress1.setText(if (zipNo.isNotEmpty()) "$address ($zipNo)" else address)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        initViews()
        setupValidation()
        setupClickListeners()
        observeViewModel()
        showVerifyDialog()
    }

    private fun observeViewModel() {
        viewModel.verifyResult.observe(this) { result ->
            if (result != null) {
                if (result.success) {
                    formContainer.visibility = View.VISIBLE
                    btnSave.visibility = View.VISIBLE
                    if (result.user != null) {
                        fillForm(result.user)
                    } else {
                        toast("정보를 불러오지 못했습니다.")
                    }
                    viewModel.loadMarketingConsent(TokenManager.getUserId(this))
                } else {
                    toast(result.message ?: "비밀번호가 올바르지 않습니다.")
                    // 검증 실패 → 재입력용 확인 다이얼로그 재표시
                    showVerifyDialog()
                }
                viewModel.onVerifyResultHandled()
            }
        }

        viewModel.saveResult.observe(this) { result ->
            if (result != null) {
                toast(result.message)
                if (result.success) finish()
                viewModel.onSaveResultHandled()
            }
        }

        // 서버 조회 성공 시에만 갱신 — null(미조회·실패)이면 로컬 캐시 값 유지
        viewModel.marketingConsent.observe(this) { consent ->
            if (consent != null) {
                suppressMarketingListener = true
                switchMarketing.isChecked = consent
                suppressMarketingListener = false
                settingsPrefs.edit().putBoolean("marketing_enabled", consent).apply()
            }
        }
    }

    private fun fillForm(user: UserResponse) {
        viewModel.onUserLoaded(user)
        isFillingForm = true
        etName.setText(user.name)
        etPhone.setText(user.phone)
        etEmail.setText(user.mail)
        etAddress1.setText(user.address.orEmpty())
        etAddress2.setText(user.addressDetail.orEmpty())
        isFillingForm = false
    }

    // 회원가입 2단계와 동일한 실시간 피드백 — 통과 시 초록, 형식 오류 시 빨강
    private fun setupValidation() {
        etName.onTextChanged {
            applyValidation(etName, tvNameMessage, when {
                it.isEmpty() -> FieldValidation.Empty
                else -> FieldValidation.Valid("✓ 입력되었습니다.")
            })
        }

        // 전화번호는 회원가입과 동일하게 하이픈 자동 삽입
        etPhone.bindPhoneFormatting { phone ->
            applyValidation(etPhone, tvPhoneMessage, when {
                phone.isEmpty() -> FieldValidation.Empty
                PhoneField.isValid(phone) -> FieldValidation.Valid(PhoneField.SUCCESS_MSG)
                else -> FieldValidation.Invalid(PhoneField.ERROR_MSG)
            })
        }

        etEmail.onTextChanged {
            applyValidation(etEmail, tvEmailMessage, when {
                it.isEmpty() -> FieldValidation.Empty
                EmailValidator.isValid(it) -> FieldValidation.Valid(EmailValidator.SUCCESS_MSG)
                else -> FieldValidation.Invalid(EmailValidator.ERROR_MSG)
            })
        }
    }

    // 저장 가능 여부 — 형식이 하나라도 어긋나면 저장 차단
    private fun validateAll(): String? {
        val name = etName.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val email = etEmail.text.toString().trim()
        return when {
            name.isEmpty() -> "이름을 입력해주세요."
            phone.isEmpty() -> "전화번호를 입력해주세요."
            !PhoneField.isValid(phone) -> "전화번호 형식을 확인해주세요."
            email.isEmpty() -> "이메일을 입력해주세요."
            !EmailValidator.isValid(email) -> "이메일 형식을 확인해주세요."
            else -> null
        }
    }

    private fun applyValidation(et: EditText, tv: TextView, validation: FieldValidation) {
        if (isFillingForm) return
        when (validation) {
            is FieldValidation.Empty -> {
                tv.isVisible = false
                et.clearInputBorder()
            }
            is FieldValidation.Valid -> showFieldMessage(et, tv, validation.message, INPUT_BORDER_VALID)
            is FieldValidation.Invalid -> showFieldMessage(et, tv, validation.message, INPUT_BORDER_ERROR)
        }
    }

    private fun showFieldMessage(et: EditText, tv: TextView, msg: String, color: Int) {
        tv.text = msg
        tv.setTextColor(color)
        tv.isVisible = true
        et.setInputBorder(color)
    }

    private fun initViews() {
        etName          = findViewById(R.id.etName)
        etPhone         = findViewById(R.id.etPhone)
        etEmail         = findViewById(R.id.etEmail)
        etAddress1      = findViewById(R.id.etAddress1)
        etAddress2      = findViewById(R.id.etAddress2)
        btnSave         = findViewById(R.id.btnSave)
        btnBack         = findViewById(R.id.btnBack)
        switchMarketing = findViewById(R.id.switchMarketing)
        formContainer   = findViewById(R.id.scrollContent)
        tvNameMessage   = findViewById(R.id.tvNameMessage)
        tvPhoneMessage  = findViewById(R.id.tvPhoneMessage)
        tvEmailMessage  = findViewById(R.id.tvEmailMessage)

        // 서버 응답 전 최근 값 표시용 로컬 캐시 — 진실의 원천은 서버
        switchMarketing.isChecked = settingsPrefs.getBoolean("marketing_enabled", false)
        switchMarketing.setOnCheckedChangeListener { _, isChecked ->
            if (suppressMarketingListener) return@setOnCheckedChangeListener
            settingsPrefs.edit().putBoolean("marketing_enabled", isChecked).apply()
            viewModel.updateMarketingConsent(TokenManager.getUserId(this), isChecked)
        }

        // 인증 전: 폼 숨김
        formContainer.visibility = View.INVISIBLE
        btnSave.visibility = View.INVISIBLE
    }

    // 진입 시 본인 확인 — 취소는 finish, 확인은 서버 검증 후 폼 표시·데이터 로드
    private fun showVerifyDialog() {
        VerifyPasswordDialog(
            context = this,
            onConfirm = { password ->
                viewModel.verifyPassword(TokenManager.getUserId(this), password)
            },
            onCancel = { finish() }
        ).show()
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }

        // 도로명 주소 검색 화면 열기
        etAddress1.setOnClickListener {
            addressLauncher.launch(Intent(this, AddressSearchActivity::class.java))
            overridePendingTransition(R.anim.detail_enter, R.anim.detail_exit)
        }

        btnSave.setOnClickListener {
            val name     = etName.text.toString().trim()
            val phone    = etPhone.text.toString().trim()
            val email    = etEmail.text.toString().trim()
            val address1 = etAddress1.text.toString().trim()
            val address2 = etAddress2.text.toString().trim()

            // 형식 오류 시 저장 차단 — 잘못된 값의 서버 반영 방지
            val error = validateAll()
            if (error != null) {
                toast(error)
                return@setOnClickListener
            }

            // 변경 없으면 서버 호출 생략
            if (!viewModel.hasChanges(name, phone, email, address1, address2)) {
                toast("변경된 내용이 없습니다.")
                return@setOnClickListener
            }

            viewModel.save(
                userId = TokenManager.getUserId(this),
                name = name,
                phone = phone,
                email = email,
                address = address1,
                addressDetail = address2
            )
        }
    }

    // 좌상단 뒤로가기 화면 공통 전환 — 알림 화면과 동일
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.detail_pop_enter, R.anim.detail_pop_exit)
    }
}
