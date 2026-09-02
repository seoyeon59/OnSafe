package com.example.on_safe.util

import android.content.Context
import android.widget.Toast

/** 화면마다 반복되던 makeText().show() 축약 — 긴 안내가 필요하면 longDuration */
fun Context.toast(message: String, longDuration: Boolean = false) {
    Toast.makeText(this, message, if (longDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}
