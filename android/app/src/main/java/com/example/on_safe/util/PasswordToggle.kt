package com.example.on_safe.util

import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.EditText
import android.widget.ImageButton
import com.example.on_safe.R

/**
 * 비밀번호 표시/숨김 토글 연결.
 * 로그인·회원가입 등 비밀번호 입력칸마다 같은 블록이 복사돼 있던 것을 통합.
 */
fun ImageButton.bindPasswordToggle(target: EditText) {
    var visible = false
    setOnClickListener {
        visible = !visible
        target.transformationMethod = if (visible) {
            HideReturnsTransformationMethod.getInstance()
        } else {
            PasswordTransformationMethod.getInstance()
        }
        target.setSelection(target.text.length)   // 커서 맨 뒤 유지
        setImageResource(if (visible) R.drawable.ic_eye_off else R.drawable.ic_eye)
    }
}
