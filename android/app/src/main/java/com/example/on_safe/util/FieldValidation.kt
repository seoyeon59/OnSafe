package com.example.on_safe.util

/**
 * 입력칸 검증 결과 — 메시지·색 판단은 뷰모델, Activity는 표시만.
 * 회원가입 2단계 / 개인정보 수정 / 비밀번호 재설정 공용.
 */
sealed class FieldValidation {
    object Empty : FieldValidation()
    data class Valid(val message: String) : FieldValidation()
    data class Invalid(val message: String) : FieldValidation()
}
