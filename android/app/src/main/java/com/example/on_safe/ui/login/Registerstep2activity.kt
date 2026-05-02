package com.example.onsafe.ui.login

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.on_safe.R

class RegisterStep2Activity : AppCompatActivity() {

    private lateinit var etId: EditText
    private lateinit var etPw: EditText
    private lateinit var etPwConfirm: EditText
    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etEmail: EditText
    private lateinit var etEmailCode: EditText
    private lateinit var etAddress: EditText
    private lateinit var etAddressDetail: EditText

    private lateinit var btnCheckId: Button
    private lateinit var btnVerifyEmail: Button
    private lateinit var btnConfirmCode: Button
    private lateinit var btnTogglePw: ImageButton
    private lateinit var btnTogglePwConfirm: ImageButton
    private lateinit var btnComplete: Button
    private lateinit var btnBack: ImageButton

    private lateinit var layoutEmailCode: LinearLayout
    private lateinit var tvIdMessage: TextView
    private lateinit var tvPwMessage: TextView
    private lateinit var tvPwConfirmMessage: TextView
    private lateinit var tvPhoneMessage: TextView
    private lateinit var tvEmailMessage: TextView
    private lateinit var tvEmailVerified: TextView
    private lateinit var tvEmailTimer: TextView
    private lateinit var tvEmailResend: TextView

    private var isPwVisible = false
    private var isPwConfirmVisible = false
    private var isIdChecked = false
    private var isEmailVerified = false

    private var emailCountDownTimer: CountDownTimer? = null

    // 유효성 상태
    private var isPwValid = false
    private var isPwConfirmValid = false
    private var isPhoneValid = false
    private var isEmailValid = false

    private var dpScale = 0f
    private var cornerPx = 0f

    private val COLOR_RED = 0xFFEF4444.toInt()
    private val COLOR_GREEN = 0xFF22C55E.toInt()
    private val COLOR_NORMAL = 0xFFF4F7FB.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_step2)

        dpScale = resources.displayMetrics.density
        cornerPx = 48f * dpScale

        etId = findViewById(R.id.etId)
        etPw = findViewById(R.id.etPw)
        etPwConfirm = findViewById(R.id.etPwConfirm)
        etName = findViewById(R.id.etName)
        etPhone = findViewById(R.id.etPhone)
        etEmail = findViewById(R.id.etEmail)
        etEmailCode = findViewById(R.id.etEmailCode)
        etAddress = findViewById(R.id.etAddress)
        etAddressDetail = findViewById(R.id.etAddressDetail)
        btnCheckId = findViewById(R.id.btnCheckId)
        btnVerifyEmail = findViewById(R.id.btnVerifyEmail)
        btnConfirmCode = findViewById(R.id.btnConfirmCode)
        btnTogglePw = findViewById(R.id.btnTogglePw)
        btnTogglePwConfirm = findViewById(R.id.btnTogglePwConfirm)
        btnComplete = findViewById(R.id.btnComplete)
        btnBack = findViewById(R.id.btnBack)
        layoutEmailCode = findViewById(R.id.layoutEmailCode)
        tvIdMessage = findViewById(R.id.tvIdMessage)
        tvPwMessage = findViewById(R.id.tvPwMessage)
        tvPwConfirmMessage = findViewById(R.id.tvPwConfirmMessage)
        tvPhoneMessage = findViewById(R.id.tvPhoneMessage)
        tvEmailMessage = findViewById(R.id.tvEmailMessage)
        tvEmailVerified = findViewById(R.id.tvEmailVerified)
        tvEmailTimer = findViewById(R.id.tvEmailTimer)
        tvEmailResend = findViewById(R.id.tvEmailResend)

        btnBack.setOnClickListener { finish() }

        // 비밀번호 토글
        btnTogglePw.setOnClickListener {
            isPwVisible = !isPwVisible
            etPw.transformationMethod = if (isPwVisible)
                HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
            etPw.setSelection(etPw.text.length)
            btnTogglePw.setImageResource(if (isPwVisible) R.drawable.ic_eye_off else R.drawable.ic_eye)
        }

        btnTogglePwConfirm.setOnClickListener {
            isPwConfirmVisible = !isPwConfirmVisible
            etPwConfirm.transformationMethod = if (isPwConfirmVisible)
                HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
            etPwConfirm.setSelection(etPwConfirm.text.length)
            btnTogglePwConfirm.setImageResource(if (isPwConfirmVisible) R.drawable.ic_eye_off else R.drawable.ic_eye)
        }

        // 아이디 — 변경 시 중복확인 초기화
        etId.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                isIdChecked = false
                tvIdMessage.visibility = View.GONE
                setInputBorderNormal(etId)
                btnCheckId.alpha = 1.0f
                btnCheckId.isEnabled = true
                updateCompleteButton()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // 비밀번호 유효성
        etPw.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val pw = s.toString()
                if (pw.isEmpty()) {
                    tvPwMessage.visibility = View.GONE
                    setInputBorderNormal(etPw)
                    isPwValid = false
                } else {
                    val regex = Regex("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@\$!%*#?&])[A-Za-z\\d@\$!%*#?&]{8,}$")
                    isPwValid = regex.matches(pw)
                    if (isPwValid) {
                        showMessage(tvPwMessage, "✓ 사용 가능한 비밀번호입니다.", COLOR_GREEN)
                        setInputBorderColor(etPw, COLOR_GREEN)
                    } else {
                        showMessage(tvPwMessage, "영문, 숫자, 특수문자 포함 8자 이상 입력해주세요.", COLOR_RED)
                        setInputBorderColor(etPw, COLOR_RED)
                    }
                }
                // 비밀번호 바뀌면 확인란도 재검사
                validatePwConfirm(etPwConfirm.text.toString())
                updateCompleteButton()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // 비밀번호 확인 유효성
        etPwConfirm.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                validatePwConfirm(s.toString())
                updateCompleteButton()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // 전화번호 유효성
        etPhone.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val phone = s.toString()
                if (phone.isEmpty()) {
                    tvPhoneMessage.visibility = View.GONE
                    setInputBorderNormal(etPhone)
                    isPhoneValid = false
                } else {
                    val regex = Regex("^01[016789]-\\d{3,4}-\\d{4}$")
                    isPhoneValid = regex.matches(phone)
                    if (isPhoneValid) {
                        showMessage(tvPhoneMessage, "✓ 올바른 전화번호입니다.", COLOR_GREEN)
                        setInputBorderColor(etPhone, COLOR_GREEN)
                    } else {
                        showMessage(tvPhoneMessage, "010-0000-0000 형식으로 입력해주세요.", COLOR_RED)
                        setInputBorderColor(etPhone, COLOR_RED)
                    }
                }
                updateCompleteButton()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // 이메일 유효성
        etEmail.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val email = s.toString()
                // 이메일 변경 시 인증 초기화
                isEmailVerified = false
                tvEmailVerified.visibility = View.GONE
                layoutEmailCode.visibility = View.GONE
                btnVerifyEmail.alpha = 1.0f
                btnVerifyEmail.isEnabled = true

                if (email.isEmpty()) {
                    tvEmailMessage.visibility = View.GONE
                    setInputBorderNormal(etEmail)
                    isEmailValid = false
                } else {
                    val regex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
                    isEmailValid = regex.matches(email)
                    if (isEmailValid) {
                        showMessage(tvEmailMessage, "✓ 올바른 이메일 형식입니다.", COLOR_GREEN)
                        setInputBorderColor(etEmail, COLOR_GREEN)
                    } else {
                        showMessage(tvEmailMessage, "올바른 이메일 형식을 입력해주세요.", COLOR_RED)
                        setInputBorderColor(etEmail, COLOR_RED)
                    }
                }
                updateCompleteButton()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // 나머지 필드
        etName.addTextChangedListener(simpleWatcher())

        // 아이디 중복 확인
        btnCheckId.setOnClickListener {
            val id = etId.text.toString().trim()
            val idRegex = Regex("^[A-Za-z0-9]{6,12}$")
            if (id.isEmpty()) {
                Toast.makeText(this, "아이디를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!idRegex.matches(id)) {
                showMessage(tvIdMessage, "영문/숫자 6~12자로 입력해주세요.", COLOR_RED)
                setInputBorderColor(etId, COLOR_RED)
                return@setOnClickListener
            }
            // TODO: API 연결 - 아이디 중복 확인
            isIdChecked = true
            showMessage(tvIdMessage, "✓ 사용 가능한 아이디입니다.", COLOR_GREEN)
            setInputBorderColor(etId, COLOR_GREEN)
            btnCheckId.isEnabled = false
            btnCheckId.alpha = 0.4f
            updateCompleteButton()
        }

        // 이메일 인증 요청
        btnVerifyEmail.setOnClickListener {
            if (!isEmailValid) {
                Toast.makeText(this, "올바른 이메일을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // TODO: API 연결 - 이메일 인증 요청
            startEmailVerification()
        }

        // 인증코드 확인
        btnConfirmCode.setOnClickListener {
            val code = etEmailCode.text.toString().trim()
            if (code.isEmpty()) {
                Toast.makeText(this, "인증코드를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // TODO: API 연결 - 인증코드 확인
            emailCountDownTimer?.cancel()
            isEmailVerified = true
            layoutEmailCode.visibility = View.GONE
            tvEmailMessage.visibility = View.GONE
            tvEmailVerified.visibility = View.VISIBLE
            updateCompleteButton()
        }

        // 재전송
        tvEmailResend.setOnClickListener {
            etEmailCode.text.clear()
            // TODO: API 연결 - 인증코드 재발송
            startEmailVerification()
            Toast.makeText(this, "인증 메일을 재발송했습니다.", Toast.LENGTH_SHORT).show()
        }

        // 주소 검색
        etAddress.setOnClickListener {
            // TODO: 도로명 주소 API 연결
            Toast.makeText(this, "주소 검색 준비 중", Toast.LENGTH_SHORT).show()
        }

        // 가입 완료
        btnComplete.setOnClickListener {
            // TODO: API 연결 - 회원가입
        }
    }

    private fun startEmailVerification() {
        btnVerifyEmail.isEnabled = false
        btnVerifyEmail.alpha = 0.4f
        layoutEmailCode.visibility = View.VISIBLE
        tvEmailResend.visibility = View.VISIBLE
        tvEmailTimer.visibility = View.VISIBLE
        btnConfirmCode.isEnabled = true
        btnConfirmCode.alpha = 1.0f
        Toast.makeText(this, "인증 메일을 발송했습니다.", Toast.LENGTH_SHORT).show()
        startEmailTimer()
    }

    private fun startEmailTimer() {
        emailCountDownTimer?.cancel()
        emailCountDownTimer = object : CountDownTimer(180_000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 60000
                val seconds = (millisUntilFinished % 60000) / 1000
                tvEmailTimer.text = String.format("%d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                tvEmailTimer.text = "0:00"
                btnConfirmCode.isEnabled = false
                btnConfirmCode.alpha = 0.4f
                btnVerifyEmail.isEnabled = true
                btnVerifyEmail.alpha = 1.0f
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        emailCountDownTimer?.cancel()
    }

    private fun validatePwConfirm(confirm: String) {
        val pw = etPw.text.toString()
        if (confirm.isEmpty()) {
            tvPwConfirmMessage.visibility = View.GONE
            setInputBorderNormal(etPwConfirm)
            isPwConfirmValid = false
            return
        }
        isPwConfirmValid = pw == confirm
        if (isPwConfirmValid) {
            showMessage(tvPwConfirmMessage, "✓ 비밀번호가 일치합니다.", COLOR_GREEN)
            setInputBorderColor(etPwConfirm, COLOR_GREEN)
        } else {
            showMessage(tvPwConfirmMessage, "비밀번호가 일치하지 않습니다.", COLOR_RED)
            setInputBorderColor(etPwConfirm, COLOR_RED)
        }
    }

    private fun showMessage(tv: TextView, msg: String, color: Int) {
        tv.text = msg
        tv.setTextColor(color)
        tv.visibility = View.VISIBLE
    }

    // 입력칸 테두리 색 변경
    private fun setInputBorderColor(et: EditText, color: Int) {
        val drawable = GradientDrawable()
        drawable.setColor(COLOR_NORMAL)
        drawable.cornerRadius = cornerPx
        drawable.setStroke((2f * dpScale).toInt(), color)
        et.background = drawable
    }

    private fun setInputBorderNormal(et: EditText) {
        et.setBackgroundResource(R.drawable.bg_input_rounded)
    }

    private fun simpleWatcher() = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) { updateCompleteButton() }
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }

    private fun updateCompleteButton() {
        val allValid = etId.text.isNotEmpty()
                && isIdChecked
                && isPwValid
                && isPwConfirmValid
                && etName.text.isNotEmpty()
                && isPhoneValid
                && isEmailValid
                && isEmailVerified

        btnComplete.isEnabled = allValid
        btnComplete.alpha = if (allValid) 1.0f else 0.4f
    }
}