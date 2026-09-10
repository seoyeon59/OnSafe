package com.example.on_safe.util

import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.example.on_safe.R

/**
 * 입력칸 검증 상태 테두리 공통 처리.
 * 로그인·회원가입 화면에 색상 상수와 GradientDrawable 조립이 복사돼 있던 것을 통합.
 */

@ColorRes
val INPUT_BORDER_VALID = R.color.status_normal

@ColorRes
val INPUT_BORDER_ERROR = R.color.status_danger

private const val BORDER_WIDTH_DP = 2f

/** 검증 결과 테두리 표시 */
fun View.setInputBorder(@ColorRes colorRes: Int) {
    background = GradientDrawable().apply {
        // bg_input_rounded와 같은 채움·곡률 — 테두리만 얹기 위해 배경을 코드로 재구성
        setColor(ContextCompat.getColor(context, R.color.surface_tab_active))
        cornerRadius = resources.getDimension(R.dimen.radius_pill)
        setStroke(
            (BORDER_WIDTH_DP * resources.displayMetrics.density).toInt(),
            ContextCompat.getColor(context, colorRes)
        )
    }
}

/** 기본 상태 복귀 */
fun View.clearInputBorder() {
    setBackgroundResource(R.drawable.bg_input_rounded)
}
