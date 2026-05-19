package com.example.onsafe.ui.login

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.on_safe.R
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.SendResetCodeRequest
import com.example.on_safe.network.dto.VerifyResetCodeRequest
import com.example.onsafe.ResetPasswordActivity
import kotlinx.coroutines.launch

class FindPwActivity : AppCompatActivity() {

    private lateinit var etUserId: EditText
    private lateinit var etEmail: EditText
    private lateinit var etCode: EditText
    private lateinit var btnRequestCode: Button
    private lateinit var btnConfirm: Button
    private lateinit var btnBack: ImageButton
    private lateinit var btnGoLogin: Button
    private lateinit var layoutCode: LinearLayout
    private lateinit var tvTimer: TextView
    private lateinit var tvResend: TextView

    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_find_pw)

        etUserId = findViewById(R.id.etUserId)
        etEmail = findViewById(R.id.etEmail)
        etCode = findViewById(R.id.etCode)
        btnRequestCode = findViewById(R.id.btnRequestCode)
        btnConfirm = findViewById(R.id.btnConfirm)
        btnBack = findViewById(R.id.btnBack)
        btnGoLogin = findViewById(R.id.btnGoLogin)
        layoutCode = findViewById(R.id.layoutCode)
        tvTimer = findViewById(R.id.tvTimer)
        tvResend = findViewById(R.id.tvResend)

        btnBack.setOnClickListener { finish() }
        btnGoLogin.setOnClickListener { finish() }

        // 재설정 코드 발송
        btnRequestCode.setOnClickListener {
            val userId = etUserId.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

            if (userId.isEmpty()) {
                Toast.makeText(this, "아이디를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (email.isEmpty() || !emailRegex.matches(email)) {
                Toast.makeText(this, "올바른 이메일 형식을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnRequestCode.isEnabled = false
            lifecycleScope.launch {
                try {
                    val response = ApiClient.api.sendResetCode(SendResetCodeRequest(userId = userId, mail = email))
                    if (response.isSuccessful && response.body()?.success == true) {
                        startVerification()
                    } else {
                        Toast.makeText(this@FindPwActivity, response.body()?.message ?: "코드 발송에 실패했습니다.", Toast.LENGTH_SHORT).show()
                        btnRequestCode.isEnabled = true
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@FindPwActivity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                    btnRequestCode.isEnabled = true
                }
            }
        }

        // 재설정 코드 확인
        btnConfirm.setOnClickListener {
            val code = etCode.text.toString().trim()
            if (code.isEmpty()) {
                Toast.makeText(this, "재설정 코드를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnConfirm.isEnabled = false
            lifecycleScope.launch {
                try {
                    val response = ApiClient.api.verifyResetCode(
                        VerifyResetCodeRequest(userId = etUserId.text.toString().trim(), code = code)
                    )
                    if (response.isSuccessful && response.body()?.success == true) {
                        countDownTimer?.cancel()
                        navigateToResetPassword()
                    } else {
                        Toast.makeText(this@FindPwActivity, response.body()?.message ?: "코드가 올바르지 않습니다.", Toast.LENGTH_SHORT).show()
                        btnConfirm.isEnabled = true
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@FindPwActivity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                    btnConfirm.isEnabled = true
                }
            }
        }

        // 재전송
        tvResend.setOnClickListener {
            etCode.text.clear()
            tvResend.visibility = View.GONE
            lifecycleScope.launch {
                try {
                    ApiClient.api.sendResetCode(SendResetCodeRequest(userId = etUserId.text.toString().trim(), mail = etEmail.text.toString().trim()))
                    startVerification()
                    Toast.makeText(this@FindPwActivity, "재설정 코드를 재발송했습니다.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@FindPwActivity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startVerification() {
        btnRequestCode.isEnabled = false
        btnRequestCode.alpha = 0.4f
        layoutCode.visibility = View.VISIBLE
        tvResend.visibility = View.VISIBLE
        tvTimer.visibility = View.VISIBLE
        btnConfirm.isEnabled = true
        btnConfirm.alpha = 1.0f
        Toast.makeText(this, "재설정 코드를 발송했습니다.", Toast.LENGTH_SHORT).show()
        startTimer()
    }

    private fun startTimer() {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(180_000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 60000
                val seconds = (millisUntilFinished % 60000) / 1000
                tvTimer.text = String.format("%d:%02d", minutes, seconds)
            }
            override fun onFinish() {
                tvTimer.text = "0:00"
                btnConfirm.isEnabled = false
                btnConfirm.alpha = 0.4f
                tvResend.visibility = View.VISIBLE
                btnRequestCode.isEnabled = true
                btnRequestCode.alpha = 1.0f
            }
        }.start()
    }

    private fun navigateToResetPassword() {
        val intent = Intent(this, ResetPasswordActivity::class.java)
        intent.putExtra("userId", etUserId.text.toString().trim())
        intent.putExtra("mode", ResetPasswordActivity.MODE_FIND_PW)
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
