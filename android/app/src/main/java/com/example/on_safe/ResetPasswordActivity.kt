package com.example.on_safe

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.on_safe.R
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.ResetPasswordRequest
import com.example.on_safe.util.PasswordValidator
import kotlinx.coroutines.launch

class ResetPasswordActivity : AppCompatActivity() {

    companion object {
        // 호출 측에서 Intent에 담아 보내는 모드값
        const val MODE_FIND_PW = "find_pw"     // 비밀번호 찾기 후 진입 (현재 비번 칸 숨김)
        const val MODE_SETTINGS = "settings"   // 설정에서 진입 (현재 비번 칸 표시)
    }

    private lateinit var layoutCurrentPw: LinearLayout
    private lateinit var etCurrentPw: EditText
    private lateinit var btnToggleCurrentPw: ImageButton

    private lateinit var etNewPw: EditText
    private lateinit var etNewPwConfirm: EditText
    private lateinit var btnToggleNewPw: ImageButton
    private lateinit var btnToggleNewPwConfirm: ImageButton
    private lateinit var btnSave: Button
    private lateinit var btnBack: ImageButton
    private lateinit var pbLoading: ProgressBar
    private lateinit var tvNewPwMessage: TextView
    private lateinit var tvNewPwConfirmMessage: TextView

    private var isCurrentPwVisible = false
    private var isNewPwVisible = false
    private var isNewPwConfirmVisible = false

    private var isNewPwValid = false
    private var isNewPwConfirmValid = false

    private val COLOR_RED = 0xFFEF4444.toInt()
    private val COLOR_GREEN = 0xFF22C55E.toInt()
    private val COLOR_NORMAL = 0xFFF4F7FB.toInt()

    // onCreate에서 한 번만 읽어 캐싱 — 매 TextWatcher 호출마다 Intent를 재조회하지 않도록
    private var currentMode = MODE_FIND_PW
    private var currentUserId = ""

    private var dpScale = 0f
    private var cornerPx = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password)

        dpScale = resources.displayMetrics.density
        cornerPx = 48f * dpScale

        layoutCurrentPw = findViewById(R.id.layoutCurrentPw)
        etCurrentPw = findViewById(R.id.etCurrentPw)
        btnToggleCurrentPw = findViewById(R.id.btnToggleCurrentPw)
        etNewPw = findViewById(R.id.etNewPw)
        etNewPwConfirm = findViewById(R.id.etNewPwConfirm)
        btnToggleNewPw = findViewById(R.id.btnToggleNewPw)
        btnToggleNewPwConfirm = findViewById(R.id.btnToggleNewPwConfirm)
        btnSave    = findViewById(R.id.btnSave)
        btnBack    = findViewById(R.id.btnBack)
        pbLoading  = findViewById(R.id.pbLoading)
        tvNewPwMessage = findViewById(R.id.tvNewPwMessage)
        tvNewPwConfirmMessage = findViewById(R.id.tvNewPwConfirmMessage)

        // MODE_SETTINGS이면 현재 비밀번호 입력란 표시
        currentMode = intent.getStringExtra("mode") ?: MODE_FIND_PW
        currentUserId = intent.getStringExtra("userId") ?: ""
        if (currentMode == MODE_SETTINGS) {
            layoutCurrentPw.visibility = View.VISIBLE
        }

        btnBack.setOnClickListener { finish() }

        btnToggleCurrentPw.setOnClickListener {
            isCurrentPwVisible = !isCurrentPwVisible
            etCurrentPw.transformationMethod = if (isCurrentPwVisible)
                HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
            etCurrentPw.setSelection(etCurrentPw.text.length)
            btnToggleCurrentPw.setImageResource(
                if (isCurrentPwVisible) R.drawable.ic_eye_off else R.drawable.ic_eye
            )
        }

        btnToggleNewPw.setOnClickListener {
            isNewPwVisible = !isNewPwVisible
            etNewPw.transformationMethod = if (isNewPwVisible)
                HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
            etNewPw.setSelection(etNewPw.text.length)
            btnToggleNewPw.setImageResource(
                if (isNewPwVisible) R.drawable.ic_eye_off else R.drawable.ic_eye
            )
        }

        btnToggleNewPwConfirm.setOnClickListener {
            isNewPwConfirmVisible = !isNewPwConfirmVisible
            etNewPwConfirm.transformationMethod = if (isNewPwConfirmVisible)
                HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
            etNewPwConfirm.setSelection(etNewPwConfirm.text.length)
            btnToggleNewPwConfirm.setImageResource(
                if (isNewPwConfirmVisible) R.drawable.ic_eye_off else R.drawable.ic_eye
            )
        }

        // 새 비밀번호 유효성
        etNewPw.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val pw = s.toString()
                if (pw.isEmpty()) {
                    tvNewPwMessage.visibility = View.GONE
                    setInputBorderNormal(etNewPw)
                    isNewPwValid = false
                } else {
                    isNewPwValid = PasswordValidator.isValid(pw)
                    if (isNewPwValid) {
                        showMessage(tvNewPwMessage, PasswordValidator.SUCCESS_MSG, COLOR_GREEN)
                        setInputBorderColor(etNewPw, COLOR_GREEN)
                    } else {
                        showMessage(tvNewPwMessage, PasswordValidator.ERROR_MSG, COLOR_RED)
                        setInputBorderColor(etNewPw, COLOR_RED)
                    }
                }
                validateConfirm(etNewPwConfirm.text.toString())
                updateSaveButton()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // 비밀번호 확인 유효성
        etNewPwConfirm.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                validateConfirm(s.toString())
                updateSaveButton()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // MODE_SETTINGS: 현재 비밀번호 입력도 버튼 활성화 조건에 포함
        etCurrentPw.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { updateSaveButton() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnSave.setOnClickListener {
            btnSave.isEnabled = false
            pbLoading.visibility = View.VISIBLE
            lifecycleScope.launch {
                try {
                    // TODO: 백엔드 currentPassword 필드 추가 시 etCurrentPw 값도 함께 전송
                    val response = ApiClient.api.resetPassword(
                        ResetPasswordRequest(userId = currentUserId, newPassword = etNewPw.text.toString().trim())
                    )
                    if (response.isSuccessful && response.body()?.success == true) {
                        Toast.makeText(this@ResetPasswordActivity, "비밀번호가 변경되었습니다.", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@ResetPasswordActivity, response.body()?.message ?: "비밀번호 변경에 실패했습니다.", Toast.LENGTH_SHORT).show()
                        btnSave.isEnabled = true
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@ResetPasswordActivity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                    btnSave.isEnabled = true
                } finally {
                    pbLoading.visibility = View.GONE
                }
            }
        }
    }

    private fun validateConfirm(confirm: String) {
        val pw = etNewPw.text.toString()
        if (confirm.isEmpty()) {
            tvNewPwConfirmMessage.visibility = View.GONE
            setInputBorderNormal(etNewPwConfirm)
            isNewPwConfirmValid = false
            return
        }
        isNewPwConfirmValid = pw == confirm
        if (isNewPwConfirmValid) {
            showMessage(tvNewPwConfirmMessage, PasswordValidator.MATCH_MSG, COLOR_GREEN)
            setInputBorderColor(etNewPwConfirm, COLOR_GREEN)
        } else {
            showMessage(tvNewPwConfirmMessage, PasswordValidator.MISMATCH_MSG, COLOR_RED)
            setInputBorderColor(etNewPwConfirm, COLOR_RED)
        }
    }

    private fun updateSaveButton() {
        val allValid = isNewPwValid && isNewPwConfirmValid &&
                (currentMode == MODE_FIND_PW || etCurrentPw.text.isNotEmpty())
        btnSave.isEnabled = allValid
        btnSave.alpha = if (allValid) 1.0f else 0.4f
    }

    private fun showMessage(tv: TextView, msg: String, color: Int) {
        tv.text = msg
        tv.setTextColor(color)
        tv.visibility = View.VISIBLE
    }

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

    // 좌상단 뒤로가기 버튼이 있는 화면 공통 — 알림 화면과 동일한 "파고들어왔다 빠져나가는" 전환
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.detail_pop_enter, R.anim.detail_pop_exit)
    }
}