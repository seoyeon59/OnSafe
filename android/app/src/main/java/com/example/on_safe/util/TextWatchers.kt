package com.example.on_safe.util

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

/**
 * TextWatcher의 빈 오버라이드 2개를 매번 적는 중복 제거용.
 * 로그인·회원가입·개인정보 수정 등 입력 화면 전반에서 공용.
 */
fun EditText.onTextChanged(action: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) = action(s.toString())
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    })
}
