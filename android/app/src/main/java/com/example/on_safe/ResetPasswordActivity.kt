package com.example.on_safe

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.on_safe.util.FieldValidation
import com.example.on_safe.util.INPUT_BORDER_ERROR
import com.example.on_safe.util.INPUT_BORDER_VALID
import com.example.on_safe.util.bindPasswordToggle
import com.example.on_safe.util.clearInputBorder
import com.example.on_safe.util.onTextChanged
import com.example.on_safe.util.setEnabledWithAlpha
import com.example.on_safe.util.setInputBorder
import com.example.on_safe.util.toast

class ResetPasswordActivity : AppCompatActivity() {

    companion object {
        // 호출부가 Intent에 담아 보내는 값 — 키·모드값 모두 여기서만 정의
        const val EXTRA_USER_ID = "userId"
        const val EXTRA_MODE = "mode"

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password)

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
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_FIND_PW
        val userId = intent.getStringExtra(EXTRA_USER_ID) ?: ""
        viewModel.init(mode, userId)
        if (mode == MODE_SETTINGS) {
            layoutCurrentPw.visibility = View.VISIBLE
        }

        btnBack.setOnClickListener { finish() }

        btnToggleCurrentPw.bindPasswordToggle(etCurrentPw)
        btnToggleNewPw.bindPasswordToggle(etNewPw)
        btnToggleNewPwConfirm.bindPasswordToggle(etNewPwConfirm)

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
            pbLoading.isVisible = state.isLoading
            // 저장 중 활성처럼 보이던 문제 — isEnabled와 알파 조건 일치
            btnSave.setEnabledWithAlpha(state.isSaveEnabled && !state.isLoading)

            applyValidation(etNewPw, tvNewPwMessage, state.newPwValidation)
            applyValidation(etNewPwConfirm, tvNewPwConfirmMessage, state.confirmValidation)
        }

        viewModel.toastMessage.observe(this) { message ->
            if (message != null) {
                toast(message)
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

    private fun applyValidation(et: EditText, tv: TextView, validation: FieldValidation) {
        when (validation) {
            is FieldValidation.Empty -> {
                tv.isVisible = false
                et.clearInputBorder()
            }
            is FieldValidation.Valid -> showMessage(et, tv, validation.message, INPUT_BORDER_VALID)
            is FieldValidation.Invalid -> showMessage(et, tv, validation.message, INPUT_BORDER_ERROR)
        }
    }

    private fun showMessage(et: EditText, tv: TextView, msg: String, color: Int) {
        tv.text = msg
        tv.setTextColor(color)
        tv.isVisible = true
        et.setInputBorder(color)
    }

    // 좌상단 뒤로가기 화면 공통 전환 — 알림 화면과 동일
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.detail_pop_enter, R.anim.detail_pop_exit)
    }
}
