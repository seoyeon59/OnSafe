package com.example.on_safe.ui.settings

import android.app.Activity
import android.content.Intent
import com.example.on_safe.ui.login.AddressSearchActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
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
        etName.setText(user.name)
        etPhone.setText(user.phone)
        etEmail.setText(user.mail)
        etAddress1.setText(user.address.orEmpty())
        etAddress2.setText(user.addressDetail.orEmpty())
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

            // 기본 유효성 검사
            if (name.isEmpty()) {
                etName.error = "이름을 입력해주세요"
                etName.requestFocus()
                return@setOnClickListener
            }
            if (phone.isEmpty()) {
                etPhone.error = "전화번호를 입력해주세요"
                etPhone.requestFocus()
                return@setOnClickListener
            }
            if (email.isEmpty()) {
                etEmail.error = "이메일을 입력해주세요"
                etEmail.requestFocus()
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
