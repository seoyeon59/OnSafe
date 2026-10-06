package com.example.on_safe.util

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.annotation.LayoutRes

// 확인 창 공통 틀 — 투명 배경(레이아웃 bg_card 곡률만 표시), 제목 없음, 화면 폭 85%, 바깥 터치로 안 닫힘.
// AlertDialog 기본 틀은 자체 곡률·여백이 덧씌워져 다른 창과 모양이 달라짐
fun cardDialog(context: Context, content: View, width: Int = defaultCardWidth(context)): Dialog =
    Dialog(context).apply {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(content)
        applyCardWindow(width)
    }

fun cardDialog(context: Context, @LayoutRes layoutRes: Int, width: Int = defaultCardWidth(context)): Dialog =
    cardDialog(context, LayoutInflater.from(context).inflate(layoutRes, null), width)

// Dialog 상속 창(VerifyPasswordDialog 등)도 같은 틀 — setContentView 뒤 호출
fun Dialog.applyCardWindow(width: Int = defaultCardWidth(context)) {
    window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
    }
    setCanceledOnTouchOutside(false)
}

private fun defaultCardWidth(context: Context): Int =
    (context.resources.displayMetrics.widthPixels * 0.85).toInt()
