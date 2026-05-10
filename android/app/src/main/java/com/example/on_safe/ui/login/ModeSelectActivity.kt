package com.example.onsafe.ui.login

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.on_safe.MainActivity
import com.example.on_safe.ui.camera.CameraModeActivity
import com.example.on_safe.R

class ModeSelectActivity : AppCompatActivity() {

    private lateinit var cardGuardian: LinearLayout
    private lateinit var cardCamera: LinearLayout
    private lateinit var btnNext: Button

    // 0 = 미선택, 1 = 보호자, 2 = 카메라
    private var selectedMode = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mode_select)

        cardGuardian = findViewById(R.id.cardGuardian)
        cardCamera = findViewById(R.id.cardCamera)
        btnNext = findViewById(R.id.btnNext)

        cardGuardian.setOnClickListener {
            selectedMode = 1
            updateCardState()
        }

        cardCamera.setOnClickListener {
            selectedMode = 2
            updateCardState()
        }

        btnNext.setOnClickListener {
            if (selectedMode == 0) return@setOnClickListener
            val intent = when (selectedMode) {
                1 -> Intent(this, MainActivity::class.java)        // 보호자 모드
                2 -> Intent(this, CameraModeActivity::class.java)  // 카메라 모드
                else -> return@setOnClickListener
            }
            intent.putExtra("selected_mode", selectedMode)
            startActivity(intent)
        }
    }

    private fun updateCardState() {
        when (selectedMode) {
            1 -> {
                // 보호자 선택 — 파란 테두리
                cardGuardian.setBackgroundResource(R.drawable.bg_mode_card_selected)
                cardCamera.setBackgroundResource(R.drawable.bg_mode_card_normal)
            }
            2 -> {
                // 카메라 선택 — 파란 테두리
                cardCamera.setBackgroundResource(R.drawable.bg_mode_card_selected)
                cardGuardian.setBackgroundResource(R.drawable.bg_mode_card_normal)
            }
        }
        // 다음 버튼 활성화
        btnNext.isEnabled = true
        btnNext.alpha = 1.0f
    }
}