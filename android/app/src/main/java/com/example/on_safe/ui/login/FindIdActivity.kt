package com.example.onsafe.ui.login

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

class FindIdActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etCode: EditText
    private lateinit var btnRequestCode: Button
    private lateinit var btnConfirm: Button
    private lateinit var btnBack: ImageButton
    private lateinit var btnGoLogin: Button
    private lateinit var layoutCode: LinearLayout
    private lateinit var layoutResult: LinearLayout
    private lateinit var tvTimer: TextView
    private lateinit var tvResend: TextView
    private lateinit var tvFoundId: TextView

    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_find_id)

        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        etCode = findViewById(R.id.etCode)
        btnRequestCode = findViewById(R.id.btnRequestCode)
        btnConfirm = findViewById(R.id.btnConfirm)
        btnBack = findViewById(R.id.btnBack)
        btnGoLogin = findViewById(R.id.btnGoLogin)
        layoutCode = findViewById(R.id.layoutCode)
        layoutResult = findViewById(R.id.layoutResult)
        tvTimer = findViewById(R.id.tvTimer)
        tvResend = findViewById(R.id.tvResend)
        tvFoundId = findViewById(R.id.tvFoundId)

        btnBack.setOnClickListener { finish() }
        btnGoLogin.setOnClickListener { finish() }

        // 인증 요청
        btnRequestCode.setOnClickListener {
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

            if (name.isEmpty()) {
                Toast.makeText(this, "이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
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

            // TODO: API 연결 - 인증코드 발송
            startVerification()
        }

        // 인증코드 확인
        btnConfirm.setOnClickListener {
            val code = etCode.text.toString().trim()
            if (code.isEmpty()) {
                Toast.makeText(this, "인증코드를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // TODO: API 연결 - 인증코드 확인 후 아이디 수신
            countDownTimer?.cancel()
            showResult("stoney109@example.com") // TODO: API 응답값으로 교체
        }

        // 재전송
        tvResend.setOnClickListener {
            etCode.text.clear()
            tvResend.visibility = View.GONE
            startVerification()
            Toast.makeText(this, "인증번호를 재발송했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startVerification() {
        btnRequestCode.isEnabled = false
        btnRequestCode.alpha = 0.4f
        layoutCode.visibility = View.VISIBLE
        tvResend.visibility = View.VISIBLE
        tvTimer.visibility = View.VISIBLE
        Toast.makeText(this, "인증번호를 발송했습니다.", Toast.LENGTH_SHORT).show()
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
                // 만료 처리
                btnConfirm.isEnabled = false
                btnConfirm.alpha = 0.4f
                tvResend.visibility = View.VISIBLE
                // 인증요청 버튼 재활성화
                btnRequestCode.isEnabled = true
                btnRequestCode.alpha = 1.0f
            }
        }.start()
    }

    private fun showResult(foundId: String) {
        tvFoundId.text = foundId
        layoutResult.visibility = View.VISIBLE
        layoutCode.visibility = View.GONE
        tvTimer.visibility = View.GONE
        btnConfirm.isEnabled = true
        btnConfirm.alpha = 1.0f
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}