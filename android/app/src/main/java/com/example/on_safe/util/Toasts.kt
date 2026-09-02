package com.example.on_safe.util

import android.content.Context
import android.widget.Toast

/** 짧은 토스트 — 화면마다 반복되던 makeText().show() 축약 */
fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
