package com.example.onsafe

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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
import androidx.lifecycle.lifecycleScope
import com.example.on_safe.R
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.ResetPasswordRequest
import kotlinx.coroutines.launch

class ResetPasswordActivity : AppCompatActivity() {

    companion object {
        const val MODE_FIND_PW = "find_pw"
        const val MODE_SETTINGS = "settings"
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
        btnSave = findViewById(R.id.btnSave)
        btnBack = findViewById(R.id.btnBack)
        tvNewPwMessage = findViewById(R.id.tvNewPwMessage)
        tvNewPwConfirmMessage = findViewById(R.id.tvNewPwConfirmMessage)

        val mode = intent.getStringExtra("mode") ?: MODE_FIND_PW
        val userId = intent.getStringExtra("userId") ?: ""

        if (mode == MODE_SETTINGS) layoutCurrentPw.visibility = View.VISIBLE

        btnBack.setOnClickListener { finish() }

        btnToggleCurrentPw.setOnClickListener {
            isCurrentPwVisible = !isCurrentPwVisible
            etCurrentPw.transformationMethod = if (isCurrentPwVisible)
                HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
            etCurrentPw.setSelection(etCurrentPw.text.length)
            btnToggleCurrentPw.setImageResource(if (isCurrentPwVisible) R.drawable.ic_eye_off else R.drawable.ic_eye)
        }

        btnToggleNewPw.setOnClickListener {
            isNewPwVisible = !isNewPwVisible
            etNewPw.transformationMethod = if (isNewPwVisible)
                HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
            etNewPw.setSelection(etNewPw.text.length)
            btnToggleNewPw.setImageResource(if (isNewPwVisible) R.drawable.ic_eye_off else R.drawable.ic_eye)
        }

        btnToggleNewPwConfirm.setOnClickListener {
            isNewPwConfirmVisible = !isNewPwConfirmVisible
            etNewPwConfirm.transformationMethod = if (isNewPwConfirmVisible)
                HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
            etNewPwConfirm.setSelection(etNewPwConfirm.text.length)
            btnToggleNewPwConfirm.setImageResource(if (isNewPwConfirmVisible) R.drawable.ic_eye_off else R.drawable.ic_eye)
        }

        etNewPw.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val pw = s.toString()
                if (pw.isEmpty()) {
                    tvNewPwMessage.visibility = View.GONE
                    setInputBorderNormal(etNewPw)
                    isNewPwValid = false
                } else {
                    val regex = Regex("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@\$!%*#?&])[A-Za-z\\d@\$!%*#?&]{8,}$")
                    isNewPwValid = regex.matches(pw)
                    if (isNewPwValid) {
                        showMessage(tvNewPwMessage, "✓ 사용 가능한 비밀번호입니다.", COLOR_GREEN)
                        setInputBorderColor(etNewPw, COLOR_GREEN)
                    } else {
                        showMessage(tvNewPwMessage, "영문, 숫자, 특수문자 포함 8자 이상 입력해주세요.", COLOR_RED)
                        setInputBorderColor(etNewPw, COLOR_RED)
                    }
                }
                validateConfirm(etNewPwConfirm.text.toString())
                updateSaveButton(mode)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        etNewPwConfirm.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                validateConfirm(s.toString())
                updateSaveButton(mode)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        etCurrentPw.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { updateSaveButton(mode) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnSave.setOnClickListener {
            btnSave.isEnabled = false
            lifecycleScope.launch {
                try {
                    val response = ApiClient.api.resetPassword(
                        ResetPasswordRequest(userId = userId, newPassword = etNewPw.text.toString().trim())
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
            showMessage(tvNewPwConfirmMessage, "✓ 비밀번호가 일치합니다.", COLOR_GREEN)
            setInputBorderColor(etNewPwConfirm, COLOR_GREEN)
        } else {
            showMessage(tvNewPwConfirmMessage, "비밀번호가 일치하지 않습니다.", COLOR_RED)
            setInputBorderColor(etNewPwConfirm, COLOR_RED)
        }
    }

    private fun updateSaveButton(mode: String) {
        val allValid = isNewPwValid && isNewPwConfirmValid &&
                (mode == MODE_FIND_PW || etCurrentPw.text.isNotEmpty())
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
}
