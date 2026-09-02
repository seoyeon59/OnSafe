package com.example.on_safe.util

import android.view.View

/**
 * 활성 상태와 흐리기 동시 적용.
 * isEnabled만 바꾸면 눌리지 않는데도 활성처럼 보여 고장으로 오인됨.
 */
fun View.setEnabledWithAlpha(enabled: Boolean) {
    isEnabled = enabled
    alpha = if (enabled) 1.0f else 0.4f
}
