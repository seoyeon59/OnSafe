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
import com.example.on_safe.R
import com.example.onsafe.ResetPasswordActivity

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

        // 코드 받기
        btnRequestCode.setOnClickListener {
            val userId = etUserId.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

            if (userId.isEmpty()) {
                Toast.makeText(this, "아이디를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (email.isEmpty()) {
                Toast.makeText(this, "이메일을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!emailRegex.matches(email)) {
                Toast.makeText(this, "올바른 이메일 형식을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // TODO: API 연결 - 재설정 코드 발송
            startVerification()
        }

        // 재설정 코드 확인
        btnConfirm.setOnClickListener {
            val code = etCode.text.toString().trim()
            if (code.isEmpty()) {
                Toast.makeText(this, "재설정 코드를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // TODO: API 연결 - 코드 확인
            countDownTimer?.cancel()
            navigateToResetPassword()
        }

        // 재전송
        tvResend.setOnClickListener {
            etCode.text.clear()
            tvResend.visibility = View.GONE
            startVerification()
            Toast.makeText(this, "재설정 코드를 재발송했습니다.", Toast.LENGTH_SHORT).show()
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
        // 필요 시 아이디/이메일 전달
        intent.putExtra("userId", etUserId.text.toString().trim())
        // FindPwActivity — navigateToResetPassword() 안
        intent.putExtra("mode", ResetPasswordActivity.MODE_FIND_PW)
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}