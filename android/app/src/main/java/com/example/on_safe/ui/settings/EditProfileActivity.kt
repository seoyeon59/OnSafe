package com.example.on_safe.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.on_safe.R
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.SendEmailCodeRequest
import com.example.on_safe.network.dto.UserUpdateRequest
import com.example.on_safe.network.dto.VerifyEmailCodeRequest
import com.example.on_safe.network.dto.VerifyPasswordRequest
import kotlinx.coroutines.launch

// 개인정보 수정 화면
// 진입 시 비밀번호 확인(POST /api/users/{userId}/verify-password) 후 폼 활성화
class EditProfileActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etEmail: EditText
    private lateinit var etAddress1: EditText
    private lateinit var etAddress2: EditText
    private lateinit var btnSave: Button
    private lateinit var btnBack: ImageButton
    private lateinit var btnRequestEmailVerify: Button
    private lateinit var formContainer: View

    private var originalEmail = ""
    private var isEmailVerified = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        initViews()
        showVerifyDialog()
    }

    private fun initViews() {
        etName               = findViewById(R.id.etName)
        etPhone              = findViewById(R.id.etPhone)
        etEmail              = findViewById(R.id.etEmail)
        etAddress1           = findViewById(R.id.etAddress1)
        etAddress2           = findViewById(R.id.etAddress2)
        btnSave              = findViewById(R.id.btnSave)
        btnBack              = findViewById(R.id.btnBack)
        btnRequestEmailVerify = findViewById(R.id.btnRequestEmailVerify)
        formContainer        = findViewById(R.id.scrollContent)

        formContainer.visibility = View.INVISIBLE
        btnSave.visibility       = View.INVISIBLE
    }

    // 진입 시 비밀번호 확인 → 서버 검증 → 성공 시 폼 표시
    private fun showVerifyDialog() {
        VerifyPasswordDialog(
            context  = this,
            onConfirm = { password -> verifyPasswordAndEnter(password) },
            onCancel  = { finish() }
        ).show()
    }

    private fun verifyPasswordAndEnter(password: String) {
        val token  = getAuthToken()
        val userId = getUserId()

        lifecycleScope.launch {
            try {
                val response = ApiClient.api.verifyPassword(
                    "Bearer $token",
                    userId,
                    VerifyPasswordRequest(currentPassword = password)
                )
                if (response.isSuccessful) {
                    formContainer.visibility = View.VISIBLE
                    btnSave.visibility       = View.VISIBLE
                    loadUserData()
                    setupClickListeners()
                } else {
                    val msg = ApiClient.parseErrorMessage(response.errorBody(), "비밀번호가 일치하지 않습니다.")
                    Toast.makeText(this@EditProfileActivity, msg, Toast.LENGTH_SHORT).show()
                    showVerifyDialog()
                }
            } catch (e: Exception) {
                Toast.makeText(this@EditProfileActivity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                showVerifyDialog()
            }
        }
    }

    private fun loadUserData() {
        val token  = getAuthToken()
        val userId = getUserId()

        lifecycleScope.launch {
            try {
                val response = ApiClient.api.getUser("Bearer $token", userId)
                if (response.isSuccessful) {
                    val user = response.body()?.data ?: return@launch
                    etName.setText(user.name)
                    etPhone.setText(user.phone)
                    etEmail.setText(user.mail)
                    etAddress1.setText(user.address ?: "")
                    etAddress2.setText(user.addressDetail ?: "")
                    originalEmail = user.mail
                }
            } catch (e: Exception) {
                Toast.makeText(this@EditProfileActivity, "정보를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }

        // 이메일 변경 시 인증 요청
        btnRequestEmailVerify.setOnClickListener {
            val newEmail = etEmail.text.toString().trim()
            if (newEmail.isEmpty()) {
                etEmail.error = "이메일을 입력해주세요"
                return@setOnClickListener
            }
            sendEmailVerificationCode(newEmail)
        }

        btnSave.setOnClickListener {
            val name     = etName.text.toString().trim()
            val phone    = etPhone.text.toString().trim()
            val email    = etEmail.text.toString().trim()
            val address1 = etAddress1.text.toString().trim()
            val address2 = etAddress2.text.toString().trim()

            if (name.isEmpty()) {
                etName.error = "이름을 입력해주세요"; return@setOnClickListener
            }
            if (phone.isEmpty()) {
                etPhone.error = "전화번호를 입력해주세요"; return@setOnClickListener
            }
            if (email.isEmpty()) {
                etEmail.error = "이메일을 입력해주세요"; return@setOnClickListener
            }
            // 이메일이 변경됐는데 인증이 안 된 경우
            if (email != originalEmail && !isEmailVerified) {
                Toast.makeText(this, "변경된 이메일을 인증해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveProfile(name, phone, email, address1, address2)
        }
    }

    private fun sendEmailVerificationCode(email: String) {
        val token = getAuthToken()
        lifecycleScope.launch {
            try {
                val response = ApiClient.api.sendEmailCode(SendEmailCodeRequest(mail = email))
                if (response.isSuccessful) {
                    Toast.makeText(this@EditProfileActivity, "인증코드가 발송되었습니다.", Toast.LENGTH_SHORT).show()
                    showEmailCodeDialog(email)
                } else {
                    val msg = ApiClient.parseErrorMessage(response.errorBody(), "이메일 발송에 실패했습니다.")
                    Toast.makeText(this@EditProfileActivity, msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@EditProfileActivity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEmailCodeDialog(email: String) {
        val input = EditText(this).apply {
            hint = "인증코드 6자리"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("이메일 인증")
            .setMessage("${email}으로 발송된 코드를 입력해주세요.")
            .setView(input)
            .setPositiveButton("확인") { _, _ ->
                val code = input.text.toString().trim()
                if (code.isNotEmpty()) verifyEmailCode(email, code)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun verifyEmailCode(email: String, code: String) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.api.verifyEmailCode(
                    VerifyEmailCodeRequest(mail = email, code = code)
                )
                if (response.isSuccessful) {
                    isEmailVerified = true
                    btnRequestEmailVerify.text = "인증완료"
                    btnRequestEmailVerify.isEnabled = false
                    Toast.makeText(this@EditProfileActivity, "이메일 인증이 완료되었습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    val msg = ApiClient.parseErrorMessage(response.errorBody(), "인증코드가 올바르지 않습니다.")
                    Toast.makeText(this@EditProfileActivity, msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@EditProfileActivity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveProfile(
        name: String, phone: String, mail: String,
        address: String, addressDetail: String
    ) {
        val token  = getAuthToken()
        val userId = getUserId()

        lifecycleScope.launch {
            try {
                val response = ApiClient.api.updateUser(
                    "Bearer $token",
                    userId,
                    UserUpdateRequest(
                        name          = name,
                        mail          = mail,
                        phone         = phone,
                        address       = address.ifEmpty { null },
                        addressDetail = addressDetail.ifEmpty { null }
                    )
                )
                if (response.isSuccessful) {
                    Toast.makeText(this@EditProfileActivity, "정보가 저장되었습니다.", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    val msg = ApiClient.parseErrorMessage(response.errorBody(), "저장에 실패했습니다.")
                    Toast.makeText(this@EditProfileActivity, msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@EditProfileActivity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getAuthToken(): String =
        getSharedPreferences("auth", Context.MODE_PRIVATE).getString("access_token", "") ?: ""

    private fun getUserId(): String =
        getSharedPreferences("auth", Context.MODE_PRIVATE).getString("user_id", "") ?: ""
}
