package com.example.on_safe.util

/**
 * 이메일 형식 검사 공통 유틸.
 * FindId / FindPw / RegisterStep2 세 곳에 같은 정규식이 복사돼 있던 것을 통합.
 */
object EmailValidator {

    private val REGEX = Regex("""^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$""")

    const val SUCCESS_MSG = "✓ 올바른 이메일 형식입니다."
    const val ERROR_MSG = "올바른 이메일 형식을 입력해주세요."

    fun isValid(email: String): Boolean = REGEX.matches(email)
}
