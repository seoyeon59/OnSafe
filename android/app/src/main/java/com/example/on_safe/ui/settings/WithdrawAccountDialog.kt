package com.example.on_safe.ui.settings

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import com.example.on_safe.R

/**
 * 회원탈퇴 확인 다이얼로그.
 * 입력란에 "회원탈퇴"를 정확히 입력해야 탈퇴하기 버튼이 활성화된다.
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

        // 초기 상태: 탈퇴하기 비활성
        updateWithdrawButton(btnWithdraw, false)

        etConfirm.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val enabled = s.toString() == "회원탈퇴"
                updateWithdrawButton(btnWithdraw, enabled)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnWithdraw.setOnClickListener {
            if (etConfirm.text.toString() == "회원탈퇴") {
                dismiss()
                onWithdraw()
            }
        }

        btnCancel.setOnClickListener { dismiss() }
    }

    private fun updateWithdrawButton(btn: TextView, enabled: Boolean) {
        btn.isClickable  = enabled
        btn.isFocusable  = enabled
        btn.setTextColor(context.getColor(if (enabled) R.color.status_danger else R.color.ink_500))
    }
}
