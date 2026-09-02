package com.example.on_safe.ui.settings

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import com.example.on_safe.R
import com.example.on_safe.util.onTextChanged

/**
 * 개인정보 수정 진입 전 본인 확인 다이얼로그.
 * 입력값 전달까지만 담당 — 서버 검증은 호출부 책임.
 */
class VerifyPasswordDialog(
    context: Context,
    private val onConfirm: (password: String) -> Unit,
    private val onCancel: () -> Unit
) : Dialog(context) {

    private var isPwVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_verify_password)

        // 배경 투명 + 둥근 모서리 유지
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.88).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        val etPassword  = findViewById<EditText>(R.id.etPassword)
        val btnTogglePw = findViewById<ImageButton>(R.id.btnTogglePw)
        val btnConfirm  = findViewById<TextView>(R.id.btnConfirm)
        val btnCancel   = findViewById<TextView>(R.id.btnCancel)

        // 초기 상태: 확인 버튼 비활성
        setConfirmEnabled(btnConfirm, false)

        // 입력 여부에 따라 확인 버튼 활성화
        etPassword.onTextChanged { setConfirmEnabled(btnConfirm, it.isNotEmpty()) }

        // 비밀번호 표시 토글
        btnTogglePw.setOnClickListener {
            isPwVisible = !isPwVisible
            etPassword.transformationMethod = if (isPwVisible)
                HideReturnsTransformationMethod.getInstance()
            else
                PasswordTransformationMethod.getInstance()
            etPassword.setSelection(etPassword.text.length)
            btnTogglePw.setImageResource(
                if (isPwVisible) R.drawable.ic_eye_off else R.drawable.ic_eye
            )
        }

        // 확인
        btnConfirm.setOnClickListener {
            val pw = etPassword.text.toString()
            if (pw.isNotEmpty()) {
                dismiss()
                onConfirm(pw)
            }
        }

        // 취소
        btnCancel.setOnClickListener {
            dismiss()
            onCancel()
        }

        // 외부 터치·뒤로가기로 닫히지 않도록 — 취소는 버튼으로만
        setCanceledOnTouchOutside(false)
        setCancelable(false)
    }

    private fun setConfirmEnabled(btn: TextView, enabled: Boolean) {
        btn.setTextColor(
            context.getColor(if (enabled) R.color.primary_blue else R.color.ink_500)
        )
        btn.isClickable = enabled
    }
}
