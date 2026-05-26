package com.example.on_safe.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.on_safe.R

// 개인정보 수정 화면 — 진입 시 비밀번호 확인 후 폼 활성화
// TODO: GET /user/profile + PUT /user/profile 연결
class EditProfileActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etEmail: EditText
    private lateinit var etAddress1: EditText
    private lateinit var etAddress2: EditText
    private lateinit var btnSave: Button
    private lateinit var btnBack: ImageButton

    // 인증 전에는 숨김 처리
    private lateinit var formContainer: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        initViews()
        showVerifyDialog()
    }

    private fun initViews() {
        etName        = findViewById(R.id.etName)
        etPhone       = findViewById(R.id.etPhone)
        etEmail       = findViewById(R.id.etEmail)
        etAddress1    = findViewById(R.id.etAddress1)
        etAddress2    = findViewById(R.id.etAddress2)
        btnSave       = findViewById(R.id.btnSave)
        btnBack       = findViewById(R.id.btnBack)
        formContainer = findViewById(R.id.scrollContent)

        // 인증 전: 폼 숨김
        formContainer.visibility = View.INVISIBLE
        btnSave.visibility = View.INVISIBLE
    }

    // 진입 시 비밀번호 확인 — 취소: finish, 확인: 폼 표시 + 데이터 로드
    private fun showVerifyDialog() {
        VerifyPasswordDialog(
            context = this,
            onConfirm = { _ ->
                // TODO: 서버에 비밀번호 검증 요청 후 성공 시 아래 실행
                formContainer.visibility = View.VISIBLE
                btnSave.visibility = View.VISIBLE
                loadUserData()
                setupClickListeners()
            },
            onCancel = { finish() }
        ).show()
    }

    private fun loadUserData() {
        // TODO: GET /user/profile → 각 필드에 채움
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }

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

            // TODO: PUT /user/profile → 성공 시 finish()
            Toast.makeText(this, "정보가 저장되었습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
