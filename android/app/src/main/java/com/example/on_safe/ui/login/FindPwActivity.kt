package com.example.on_safe.ui.login

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.on_safe.R
import com.example.on_safe.ResetPasswordActivity
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.SendResetCodeRequest
import com.example.on_safe.network.dto.VerifyResetCodeRequest
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
    private lateinit var pbLoading: ProgressBar

    private var countDownTimer: CountDownTimer? = null

    // navigateToResetPassword()에서 전환(다음 화면)과 finish()(스택 정리)를 함께 호출할 때,
    // finish()의 기본 "뒤로 나가는" 전환이 방금 지정한 "다음으로 넘어가는" 전환을 덮어쓰지 않도록 함
    private var suppressFinishTransition = false

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
        tvResend  = findViewById(R.id.tvResend)
        pbLoading = findViewById(R.id.pbLoading)

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
            if (email.isEmpty()) {
                Toast.makeText(this, "이메일을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!emailRegex.matches(email)) {
                Toast.makeText(this, "올바른 이메일 형식을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnRequestCode.isEnabled = false
            pbLoading.visibility = View.VISIBLE
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
                } finally {
                    pbLoading.visibility = View.GONE
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
            pbLoading.visibility = View.VISIBLE
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
                } finally {
                    pbLoading.visibility = View.GONE
                }
            }
        }

        // 재전송
        tvResend.setOnClickListener {
            etCode.text.clear()
            tvResend.visibility = View.GONE
            lifecycleScope.launch {
                try {
                    val response = ApiClient.api.sendResetCode(
                        SendResetCodeRequest(userId = etUserId.text.toString().trim(), mail = etEmail.text.toString().trim())
                    )
                    if (response.isSuccessful && response.body()?.success == true) {
                        startVerification()
                        Toast.makeText(this@FindPwActivity, "재설정 코드를 재발송했습니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        // 재발송 실패 — 다시 시도할 수 있도록 재전송 버튼 복구
                        tvResend.visibility = View.VISIBLE
                        Toast.makeText(
                            this@FindPwActivity,
                            response.body()?.message ?: "재설정 코드 재발송에 실패했습니다.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    tvResend.visibility = View.VISIBLE
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
        overridePendingTransition(R.anim.detail_enter, R.anim.detail_exit)
        suppressFinishTransition = true
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }

    // 좌상단 뒤로가기 버튼이 있는 화면 공통 — 알림 화면과 동일한 "파고들어왔다 빠져나가는" 전환
    // (다음 화면으로 넘어가며 스택 정리 차원에서 finish()를 호출하는 경우는 예외)
    override fun finish() {
        super.finish()
        if (!suppressFinishTransition) {
            overridePendingTransition(R.anim.detail_pop_enter, R.anim.detail_pop_exit)
        }
    }
}