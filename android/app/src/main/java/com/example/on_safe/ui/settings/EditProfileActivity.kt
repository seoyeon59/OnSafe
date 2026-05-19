package com.example.on_safe.ui.settings

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.on_safe.R

/**
 * 개인정보 수정 화면.
 * 보호자 이름, 전화번호, 이메일, 주소(도로명+상세)를 수정한다.
 * TODO: SharedPreferences / 서버 API와 연결하여 실제 데이터를 불러오고 저장한다.
 */
class EditProfileActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etEmail: EditText
    private lateinit var etAddress1: EditText
    private lateinit var etAddress2: EditText
    private lateinit var btnSave: Button
    private lateinit var btnBack: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        initViews()
        loadUserData()
        setupClickListeners()
    }

    private fun initViews() {
        etName     = findViewById(R.id.etName)
        etPhone    = findViewById(R.id.etPhone)
        etEmail    = findViewById(R.id.etEmail)
        etAddress1 = findViewById(R.id.etAddress1)
        etAddress2 = findViewById(R.id.etAddress2)
        btnSave    = findViewById(R.id.btnSave)
        btnBack    = findViewById(R.id.btnBack)
    }

    /**
     * TODO: SharedPreferences 또는 서버에서 저장된 정보를 불러와 입력 필드에 채운다.
     */
    private fun loadUserData() {
        // 예시: SharedPreferences에서 읽기
        // val prefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        // etName.setText(prefs.getString("name", ""))
        // etPhone.setText(prefs.getString("phone", ""))
        // etEmail.setText(prefs.getString("email", ""))
        // etAddress1.setText(prefs.getString("address1", ""))
        // etAddress2.setText(prefs.getString("address2", ""))
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

            // TODO: 서버 API로 저장 처리
            // 임시: SharedPreferences 저장
            // val prefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
            // prefs.edit().apply {
            //     putString("name", name)
            //     putString("phone", phone)
            //     putString("email", email)
            //     putString("address1", address1)
            //     putString("address2", address2)
            // }.apply()

            Toast.makeText(this, "정보가 저장되었습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
