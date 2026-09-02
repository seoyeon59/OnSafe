package com.example.on_safe.ui.settings

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import com.example.on_safe.R
import com.example.on_safe.util.onTextChanged

/**
 * 회원탈퇴 확인 다이얼로그.
 * 확인 문구를 정확히 입력해야 탈퇴 버튼 활성화.
 */
class WithdrawAccountDialog(
    context: Context,
    private val onWithdraw: () -> Unit
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_withdraw_account)

        // 배경 투명 + 모서리 둥글게
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.88).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        val etConfirm = findViewById<EditText>(R.id.etWithdrawConfirm)
        val btnWithdraw = findViewById<TextView>(R.id.btnWithdraw)
        val btnCancel = findViewById<TextView>(R.id.btnCancel)

        // 초기 상태: 탈퇴 비활성
        setWithdrawEnabled(btnWithdraw, false)

        etConfirm.onTextChanged { setWithdrawEnabled(btnWithdraw, it == CONFIRM_PHRASE) }

        btnWithdraw.setOnClickListener {
            if (etConfirm.text.toString() == CONFIRM_PHRASE) {
                dismiss()
                onWithdraw()
            }
        }

        btnCancel.setOnClickListener { dismiss() }
    }

    // 색상과 클릭 가능 여부를 함께 조정 — VerifyPasswordDialog와 동일 패턴
    private fun setWithdrawEnabled(btn: TextView, enabled: Boolean) {
        btn.setTextColor(
            context.getColor(if (enabled) R.color.status_danger else R.color.ink_500)
        )
        btn.isClickable = enabled
    }

    private companion object {
        const val CONFIRM_PHRASE = "회원탈퇴"
    }
}
