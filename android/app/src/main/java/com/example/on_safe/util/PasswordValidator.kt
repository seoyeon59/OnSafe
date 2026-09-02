package com.example.on_safe.util

/**
 * 비밀번호 유효성 검사 공통 유틸.
 * RegisterStep2Activity / ResetPasswordActivity에서 공통 사용.
 */
object PasswordValidator {

    // 영문 + 숫자 + 특수문자(@$!%*#?&) 포함 8자 이상
    private val REGEX = Regex("""^(?=.*[A-Za-z])(?=.*\d)(?=.*[@$!%*#?&])[A-Za-z\d@$!%*#?&]{8,}$""")

    const val ERROR_MSG = "영문, 숫자, 특수문자 포함 8자 이상 입력해주세요."
    const val SUCCESS_MSG = "✓ 사용 가능한 비밀번호입니다."
    const val MATCH_MSG = "✓ 비밀번호가 일치합니다."
    const val MISMATCH_MSG = "비밀번호가 일치하지 않습니다."

    fun isValid(password: String): Boolean = REGEX.matches(password)
}
