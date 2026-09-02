package com.example.on_safe.util

import android.widget.EditText

/**
 * 전화번호 형식 검사·하이픈 포맷 공통 유틸.
 * RegisterStep2 / EditProfile에 같은 정규식과 포맷 함수가 복사돼 있던 것을 통합.
 */
object PhoneField {

    // 010은 항상 11자리(가운데 4자리), 구번호(011/016~019)만 3~4자리 허용
    private val REGEX = Regex("""^(010-\d{4}|01[16789]-\d{3,4})-\d{4}$""")

    const val SUCCESS_MSG = "✓ 올바른 전화번호입니다."
    const val ERROR_MSG = "010-0000-0000 형식으로 입력해주세요."

    fun isValid(phone: String): Boolean = REGEX.matches(phone)

    /** 숫자만 남긴 문자열에 하이픈 삽입 (3-3(또는4)-4) */
    fun format(digits: String): String = when {
        digits.length <= 3 -> digits
        digits.length <= 7 -> "${digits.substring(0, 3)}-${digits.substring(3)}"
        else -> "${digits.substring(0, 3)}-${digits.substring(3, digits.length - 4)}-${digits.substring(digits.length - 4)}"
    }

    /** 입력 원문에서 숫자만 뽑아 최대 11자리로 자른 뒤 포맷 */
    fun formatInput(raw: String): String = format(raw.filter { it.isDigit() }.take(11))
}

/**
 * 입력 중 하이픈 자동 삽입 연결 — 콜백은 포맷이 끝난 최종 문자열을 받음.
 * setText가 리스너를 다시 부르므로 재진입 시에는 포맷·콜백 모두 건너뜀.
 */
fun EditText.bindPhoneFormatting(onFormatted: (String) -> Unit) {
    var formatting = false
    onTextChanged { raw ->
        if (formatting) return@onTextChanged
        formatting = true
        val formatted = PhoneField.formatInput(raw)
        if (formatted != raw) {
            setText(formatted)
            setSelection(formatted.length)   // 커서 맨 뒤로
        }
        formatting = false
        onFormatted(formatted)
    }
}
