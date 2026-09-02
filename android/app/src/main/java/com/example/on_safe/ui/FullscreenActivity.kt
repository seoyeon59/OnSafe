package com.example.on_safe.ui

import android.os.Bundle
import android.view.MotionEvent
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.example.on_safe.R
import com.example.on_safe.util.toast

class FullscreenActivity : AppCompatActivity() {

    private var lastHintTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 가로 방향은 매니페스트의 sensorLandscape로 처리 (여기서 고정하면 양방향 회전이 막힘)
        setContentView(R.layout.activity_fullscreen)

        showExitHint()
        findViewById<ImageButton>(R.id.btnBackFullscreen).setOnClickListener { finish() }
    }

    // 뒤로가기·닫기 모두 이 finish()를 거친다 — 퇴장은 회전 대신 detail_pop 전환 사용
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.detail_pop_enter, R.anim.detail_pop_exit)
    }

    // 전체화면에는 안내 문구를 띄울 자리가 없어 터치할 때마다 재출력
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) showExitHint()
        return super.onTouchEvent(event)
    }

    // 쿨타임 — 연속 터치로 토스트가 줄줄이 쌓이는 것 방지 (진입 직후 터치도 동일 적용)
    private fun showExitHint() {
        val now = System.currentTimeMillis()
        if (now - lastHintTime < HINT_COOLDOWN_MS) return
        lastHintTime = now
        toast("나가려면 뒤로가기를 누르세요")
    }

    private companion object {
        const val HINT_COOLDOWN_MS = 1500L
    }
}
