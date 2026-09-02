package com.example.on_safe

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.on_safe.util.onTextChanged

class ResetPasswordActivity : AppCompatActivity() {

    companion object {
        // 호출부가 Intent에 담아 보내는 모드값
        const val MODE_FIND_PW = "find_pw"     // 비밀번호 찾기 후 진입 (현재 비번 칸 숨김)
        const val MODE_SETTINGS = "settings"   // 설정에서 진입 (현재 비번 칸 표시)
    }

    private val viewModel: ResetPasswordViewModel by viewModels()

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
        btnSave    = findViewById(R.id.btnSave)
        btnBack    = findViewById(R.id.btnBack)
        pbLoading  = findViewById(R.id.pbLoading)
        tvNewPwMessage = findViewById(R.id.tvNewPwMessage)
        tvNewPwConfirmMessage = findViewById(R.id.tvNewPwConfirmMessage)

        // MODE_SETTINGS이면 현재 비밀번호 입력란 표시
        val mode = intent.getStringExtra("mode") ?: MODE_FIND_PW
        val userId = intent.getStringExtra("userId") ?: ""
        viewModel.init(mode, userId)
        if (mode == MODE_SETTINGS) {
            layoutCurrentPw.visibility = View.VISIBLE
        }

        btnBack.setOnClickListener { finish() }

        // 눈 아이콘 3개 동작 동일 — 공통 헬퍼로 통합
        setupPasswordToggle(etCurrentPw, btnToggleCurrentPw)
        setupPasswordToggle(etNewPw, btnToggleNewPw)
        setupPasswordToggle(etNewPwConfirm, btnToggleNewPwConfirm)

        // 유효성 판단은 뷰모델 담당 — Activity는 입력 전달·결과 표시만
        etNewPw.onTextChanged(viewModel::onNewPasswordChanged)
        etNewPwConfirm.onTextChanged(viewModel::onConfirmChanged)
        // MODE_SETTINGS: 현재 비밀번호는 버튼 활성화 조건이자 서버 본인확인 전송값
        etCurrentPw.onTextChanged(viewModel::onCurrentPasswordChanged)

        btnSave.setOnClickListener {
            viewModel.save()
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            pbLoading.visibility = if (state.isLoading) View.VISIBLE else View.GONE
            // 저장 중 활성처럼 보이던 문제 — isEnabled와 알파 조건 일치
            val saveEnabled = state.isSaveEnabled && !state.isLoading
            btnSave.isEnabled = saveEnabled
            btnSave.alpha = if (saveEnabled) 1.0f else 0.4f

            applyValidation(etNewPw, tvNewPwMessage, state.newPwValidation)
            applyValidation(etNewPwConfirm, tvNewPwConfirmMessage, state.confirmValidation)
        }

        viewModel.toastMessage.observe(this) { message ->
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                viewModel.onToastShown()
            }
        }

        viewModel.saveSuccess.observe(this) { success ->
            if (success) {
                viewModel.onSaveHandled()
                finish()
            }
        }
    }

    // 표시/숨김 전환 + 커서 끝 유지 + 아이콘 교체
    private fun setupPasswordToggle(et: EditText, btn: ImageButton) {
        var visible = false
        btn.setOnClickListener {
            visible = !visible
            et.transformationMethod = if (visible)
                HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
            et.setSelection(et.text.length)
            btn.setImageResource(if (visible) R.drawable.ic_eye_off else R.drawable.ic_eye)
        }
    }

    private fun applyValidation(et: EditText, tv: TextView, validation: FieldValidation) {
        when (validation) {
            is FieldValidation.Empty -> {
                tv.visibility = View.GONE
                setInputBorderNormal(et)
            }
            is FieldValidation.Valid -> {
                showMessage(tv, validation.message, COLOR_GREEN)
                setInputBorderColor(et, COLOR_GREEN)
            }
            is FieldValidation.Invalid -> {
                showMessage(tv, validation.message, COLOR_RED)
                setInputBorderColor(et, COLOR_RED)
            }
        }
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

    // 좌상단 뒤로가기 화면 공통 전환 — 알림 화면과 동일
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.detail_pop_enter, R.anim.detail_pop_exit)
    }
}
