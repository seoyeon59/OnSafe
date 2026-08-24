package com.example.on_safe.ui.settings

import android.app.Activity
import android.content.Intent
import com.example.on_safe.ui.login.AddressSearchActivity
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.example.on_safe.FieldValidation
import com.example.on_safe.R
import com.example.on_safe.network.dto.UserResponse
import com.example.on_safe.util.TokenManager

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

    // 서버에서 받아온 값으로 스위치를 프로그래밍적으로 세팅할 때 리스너 재발화(재-PUT) 방지
    private var suppressMarketingListener = false

    // 전화번호 자동 하이픈 처리 중 재귀 호출 방지
    private var isFormattingPhone = false

    // 서버 값으로 폼을 채울 때는 검증 문구를 띄우지 않는다 — 화면에 들어오자마자
    // 모든 칸이 초록 테두리로 바뀌면 사용자가 뭔가 입력한 것으로 오해한다
    private var isFillingForm = false

    private lateinit var tvNameMessage: TextView
    private lateinit var tvPhoneMessage: TextView
    private lateinit var tvEmailMessage: TextView

    private val COLOR_RED = 0xFFEF4444.toInt()
    private val COLOR_GREEN = 0xFF22C55E.toInt()
    private val COLOR_INPUT_BG = 0xFFF4F7FB.toInt()

    private companion object {
        val PHONE_REGEX = Regex("^(010-\\d{4}|01[16789]-\\d{3,4})-\\d{4}$")
        val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }

    private val settingsPrefs by lazy {
        getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
    }

    // 주소 검색 결과를 받아오는 런처
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
                        Toast.makeText(this, "정보를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                    }
                    viewModel.loadMarketingConsent(TokenManager.getUserId(this))
                } else {
                    Toast.makeText(this, result.message ?: "비밀번호가 올바르지 않습니다.", Toast.LENGTH_SHORT).show()
                    // 검증 실패 → 재입력할 수 있도록 확인 다이얼로그 다시 표시
                    showVerifyDialog()
                }
                viewModel.onVerifyResultHandled()
            }
        }

        viewModel.saveResult.observe(this) { result ->
            if (result != null) {
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                if (result.success) finish()
                viewModel.onSaveResultHandled()
            }
        }

        // 서버 조회 성공 시에만 갱신 — null(미조회/실패)이면 로컬 캐시로 표시된 값을 그대로 둔다
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

    // 회원가입 2단계와 같은 방식의 실시간 피드백 — 통과 시 초록, 형식 오류 시 빨강
    private fun setupValidation() {
        etName.addTextChangedListener(afterChanged {
            applyValidation(etName, tvNameMessage, when {
                it.isEmpty() -> FieldValidation.Empty
                else -> FieldValidation.Valid("✓ 입력되었습니다.")
            })
        })

        // 전화번호는 회원가입과 동일하게 하이픈을 자동으로 넣어준다
        etPhone.addTextChangedListener(afterChanged {
            if (!isFormattingPhone) {
                isFormattingPhone = true
                val digits = it.filter { c -> c.isDigit() }.take(11)
                val formatted = formatPhone(digits)
                if (formatted != it) {
                    etPhone.setText(formatted)
                    etPhone.setSelection(formatted.length)
                }
                isFormattingPhone = false
            }
            val phone = etPhone.text.toString()
            applyValidation(etPhone, tvPhoneMessage, when {
                phone.isEmpty() -> FieldValidation.Empty
                PHONE_REGEX.matches(phone) -> FieldValidation.Valid("✓ 올바른 전화번호입니다.")
                else -> FieldValidation.Invalid("010-0000-0000 형식으로 입력해주세요.")
            })
        })

        etEmail.addTextChangedListener(afterChanged {
            applyValidation(etEmail, tvEmailMessage, when {
                it.isEmpty() -> FieldValidation.Empty
                EMAIL_REGEX.matches(it) -> FieldValidation.Valid("✓ 올바른 이메일 형식입니다.")
                else -> FieldValidation.Invalid("올바른 이메일 형식을 입력해주세요.")
            })
        })
    }

    // 저장 가능 여부 — 하나라도 형식이 어긋나면 저장을 막는다
    private fun validateAll(): String? {
        val name = etName.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val email = etEmail.text.toString().trim()
        return when {
            name.isEmpty() -> "이름을 입력해주세요."
            phone.isEmpty() -> "전화번호를 입력해주세요."
            !PHONE_REGEX.matches(phone) -> "전화번호 형식을 확인해주세요."
            email.isEmpty() -> "이메일을 입력해주세요."
            !EMAIL_REGEX.matches(email) -> "이메일 형식을 확인해주세요."
            else -> null
        }
    }

    private fun applyValidation(et: EditText, tv: TextView, validation: FieldValidation) {
        if (isFillingForm) return
        when (validation) {
            is FieldValidation.Empty -> {
                tv.visibility = View.GONE
                et.setBackgroundResource(R.drawable.bg_input_rounded)
            }
            is FieldValidation.Valid -> showFieldMessage(et, tv, validation.message, COLOR_GREEN)
            is FieldValidation.Invalid -> showFieldMessage(et, tv, validation.message, COLOR_RED)
        }
    }

    private fun showFieldMessage(et: EditText, tv: TextView, msg: String, color: Int) {
        tv.text = msg
        tv.setTextColor(color)
        tv.visibility = View.VISIBLE
        et.background = GradientDrawable().apply {
            setColor(COLOR_INPUT_BG)
            cornerRadius = 48f * resources.displayMetrics.density
            setStroke((2f * resources.displayMetrics.density).toInt(), color)
        }
    }

    // 010-1234-5678 형태로 하이픈 자동 삽입 (회원가입 화면과 동일 규칙)
    private fun formatPhone(digits: String): String = when {
        digits.length <= 3 -> digits
        digits.length <= 7 -> "${digits.substring(0, 3)}-${digits.substring(3)}"
        else -> "${digits.substring(0, 3)}-${digits.substring(3, digits.length - 4)}-${digits.substring(digits.length - 4)}"
    }

    private fun afterChanged(action: (String) -> Unit) = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) = action(s.toString())
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
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

        // 오프라인/서버 응답 전에도 최근 값을 바로 보여주기 위한 로컬 캐시 — 진실의 원천은 서버(GET/PUT /api/settings/marketing)
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

    // 진입 시 비밀번호 확인 — 취소: finish, 확인: 서버 검증 후 성공 시 폼 표시 + 데이터 로드
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

        //도로명 주소 검색 화면 열기
        etAddress1.setOnClickListener {
            addressLauncher.launch(Intent(this,
                AddressSearchActivity::class.java))
            overridePendingTransition(R.anim.detail_enter, R.anim.detail_exit)
        }

        btnSave.setOnClickListener {
            val name     = etName.text.toString().trim()
            val phone    = etPhone.text.toString().trim()
            val email    = etEmail.text.toString().trim()
            val address1 = etAddress1.text.toString().trim()
            val address2 = etAddress2.text.toString().trim()

            // 형식이 어긋나면 저장을 막는다 — 잘못된 값이 그대로 서버에 들어가는 걸 방지
            val error = validateAll()
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 바뀐 게 없으면 굳이 서버에 쓰지 않는다
            if (!viewModel.hasChanges(name, phone, email, address1, address2)) {
                Toast.makeText(this, "변경된 내용이 없습니다.", Toast.LENGTH_SHORT).show()
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

    // 좌상단 뒤로가기 버튼이 있는 화면 공통 — 알림 화면과 동일한 "파고들어왔다 빠져나가는" 전환
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.detail_pop_enter, R.anim.detail_pop_exit)
    }
}
