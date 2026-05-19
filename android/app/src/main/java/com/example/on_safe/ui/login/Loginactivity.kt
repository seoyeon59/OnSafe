package com.example.onsafe.ui.login

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.on_safe.R
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.LoginRequest
import kotlinx.coroutines.launch

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

        btnTogglePw.setOnClickListener {
            isPwVisible = !isPwVisible
            if (isPwVisible) {
                etPw.transformationMethod = HideReturnsTransformationMethod.getInstance()
                btnTogglePw.setImageResource(R.drawable.ic_eye_off)
            } else {
                etPw.transformationMethod = PasswordTransformationMethod.getInstance()
                btnTogglePw.setImageResource(R.drawable.ic_eye)
            }
            etPw.setSelection(etPw.text.length)
        }

        btnLogin.setOnClickListener {
            val id = etId.text.toString().trim()
            val pw = etPw.text.toString().trim()

            if (id.isEmpty() || pw.isEmpty()) {
                Toast.makeText(this, "아이디와 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

            btnLogin.isEnabled = false
            lifecycleScope.launch {
                try {
                    val response = ApiClient.api.login(LoginRequest(userId = id, password = pw, deviceId = deviceId))
                    if (response.isSuccessful && response.body()?.success == true) {
                        val data = response.body()!!.data!!
                        getSharedPreferences("auth", MODE_PRIVATE).edit()
                            .putString("access_token", data.accessToken)
                            .putString("refresh_token", data.refreshToken)
                            .putString("user_id", data.userId)
                            .apply()
                        Log.d("Login", "저장 완료 — userId=${data.userId}, accessToken=${data.accessToken.take(20)}...")
                        Toast.makeText(this@LoginActivity, "로그인 성공", Toast.LENGTH_SHORT).show()
                        // TODO: 메인 화면으로 이동
                    } else {
                        val message = response.body()?.message
                            ?: ApiClient.parseErrorMessage(response.errorBody(), "로그인에 실패했습니다.")
                        Log.w("Login", "실패 — HTTP ${response.code()}: $message")
                        Toast.makeText(this@LoginActivity, message, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("Login", "네트워크 오류", e)
                    Toast.makeText(this@LoginActivity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                } finally {
                    btnLogin.isEnabled = true
                }
            }
        }

        tvFindId.setOnClickListener {
            startActivity(Intent(this, FindIdActivity::class.java))
        }

        tvFindPw.setOnClickListener {
            startActivity(Intent(this, FindPwActivity::class.java))
        }

        tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterStep1Activity::class.java))
        }
    }
}
