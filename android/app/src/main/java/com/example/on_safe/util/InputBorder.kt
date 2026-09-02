package com.example.on_safe.util

import android.graphics.drawable.GradientDrawable
import android.view.View
import com.example.on_safe.R

/**
 * 입력칸 검증 상태 테두리 공통 처리.
 * 로그인·회원가입 화면에 색상 상수와 GradientDrawable 조립이 복사돼 있던 것을 통합.
 */

const val INPUT_BORDER_VALID = 0xFF22C55E.toInt()
const val INPUT_BORDER_ERROR = 0xFFEF4444.toInt()

// bg_input_rounded와 동일한 채움색 — 테두리만 얹기 위해 배경을 코드로 재구성
private const val INPUT_FILL = 0xFFF4F7FB.toInt()
private const val BORDER_WIDTH_DP = 2f
private const val CORNER_RADIUS_DP = 48f

/** 검증 결과 테두리 표시 */
fun View.setInputBorder(color: Int) {
    val density = resources.displayMetrics.density
    background = GradientDrawable().apply {
        setColor(INPUT_FILL)
        cornerRadius = CORNER_RADIUS_DP * density
        setStroke((BORDER_WIDTH_DP * density).toInt(), color)
    }
}

/** 기본 상태 복귀 */
fun View.clearInputBorder() {
    setBackgroundResource(R.drawable.bg_input_rounded)
}
