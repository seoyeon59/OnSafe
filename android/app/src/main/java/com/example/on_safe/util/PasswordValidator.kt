package com.example.on_safe.util

/**
 * 비밀번호 유효성 검사 공통 유틸.
 * RegisterStep2Activity / ResetPasswordActivity에서 공통 사용.
 *
 * 서버 검증 규칙과 동일하게 유지 —
 * Backend: RegisterRequest / ResetPasswordRequest / UserUpdateRequest 의 @Pattern.
 */
object PasswordValidator {

    // 영문 + 숫자 + 특수문자(@$!%*#?&) 모두 필수, 8자 이상.
    // (?s) DOTALL — "."이 개행(\n)도 매치. 클립보드 붙여넣기·IME 이슈로 개행이 섞여도
    //             필수 3종 충족하면 통과시킨다 (서버 로직과 일치).
    // 허용 문자 세트는 제한하지 않음 → 하이픈·언더스코어·공백 등도 통과.
    private val REGEX = Regex("""(?s)^(?=.*[A-Za-z])(?=.*\d)(?=.*[@$!%*#?&]).{8,}$""")

    const val ERROR_MSG = "영문, 숫자, 특수문자(@\$!%*#?&) 포함 8자 이상 입력해주세요."
    const val SUCCESS_MSG = "✓ 사용 가능한 비밀번호입니다."
    const val MATCH_MSG = "✓ 비밀번호가 일치합니다."
    const val MISMATCH_MSG = "비밀번호가 일치하지 않습니다."

    fun isValid(password: String): Boolean = REGEX.matches(password)
}