package com.example.on_safe.ui

import android.os.Bundle
import android.view.MotionEvent
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.on_safe.R

class FullscreenActivity : AppCompatActivity() {

    private var lastToastTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 가로 방향은 매니페스트의 sensorLandscape로 처리 (여기서 고정하면 양방향 회전이 막힘)
        setContentView(R.layout.activity_fullscreen)

        Toast.makeText(this, "나가려면 뒤로가기를 누르세요", Toast.LENGTH_SHORT).show()

        findViewById<ImageButton>(R.id.btnBackFullscreen).setOnClickListener {
            finish()
        }
    }

    // 뒤로가기·닫기 버튼 모두 이 finish()를 거치므로 여기 한 곳에서만 처리하면 됨.
    // 들어올 땐 회전하며 확대되지만(fullscreen_enter), 나갈 땐 회전으로 되감지 않고
    // 다른 뒤로가기 화면들과 동일한 detail_pop 전환을 사용 — 회전 퇴장이 어색하다는 피드백 반영
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.detail_pop_enter, R.anim.detail_pop_exit)
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