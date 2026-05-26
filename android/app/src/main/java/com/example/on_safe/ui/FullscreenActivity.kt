package com.example.on_safe.ui

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.on_safe.R

class FullscreenActivity : AppCompatActivity() {

    private var lastToastTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 가로 고정
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setContentView(R.layout.activity_fullscreen)

        Toast.makeText(this, "나가려면 뒤로가기를 누르세요", Toast.LENGTH_SHORT).show()
    }

    // 화면 터치 감지 - 토스트 재출력 (1.5초 쿨타임)
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val now = System.currentTimeMillis()
            if (now - lastToastTime > 1500) {
                Toast.makeText(this, "나가려면 뒤로가기를 누르세요", Toast.LENGTH_SHORT).show()
                lastToastTime = now
            }
        }
        return super.onTouchEvent(event)
    }
}