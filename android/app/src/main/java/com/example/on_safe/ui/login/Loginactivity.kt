package com.example.on_safe.ui.login

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.on_safe.R

class LoginActivity : AppCompatActivity() {

    private lateinit var etId: EditText
    private lateinit var etPw: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnTogglePw: ImageButton
    private lateinit var tvFindId: TextView
    private lateinit var tvFindPw: TextView
    private lateinit var tvRegister: TextView

    private var isPwVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etId = findViewById(R.id.etId)
        etPw = findViewById(R.id.etPw)
        btnLogin = findViewById(R.id.btnLogin)
        btnTogglePw = findViewById(R.id.btnTogglePw)
        tvFindId = findViewById(R.id.tvFindId)
        tvFindPw = findViewById(R.id.tvFindPw)
        tvRegister = findViewById(R.id.tvRegister)

        // 비밀번호 표시/숨김 토글
        btnTogglePw.setOnClickListener {
            isPwVisible = !isPwVisible
            if (isPwVisible) {
                etPw.transformationMethod = HideReturnsTransformationMethod.getInstance()
                btnTogglePw.setImageResource(R.drawable.ic_eye_off)
            } else {
                etPw.transformationMethod = PasswordTransformationMethod.getInstance()
                btnTogglePw.setImageResource(R.drawable.ic_eye)
            }
            // 커서를 텍스트 끝으로 이동
            etPw.setSelection(etPw.text.length)
        }

        // 로그인 버튼
        btnLogin.setOnClickListener {
            val id = etId.text.toString().trim()
            val pw = etPw.text.toString().trim()

            if (id.isEmpty() || pw.isEmpty()) {
                Toast.makeText(this, "아이디와 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // TODO: API 연결 (ApiClient 사용)
            // 예시: loginWithServer(id, pw)
        }

        // 아이디 찾기
        tvFindId.setOnClickListener {
            startActivity(Intent(this, FindIdActivity::class.java))
        }

        // 비밀번호 찾기
        tvFindPw.setOnClickListener {
            startActivity(Intent(this, FindPwActivity::class.java))
        }

        // 회원가입
        tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterStep1Activity::class.java))
        }
    }
}