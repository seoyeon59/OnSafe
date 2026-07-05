package com.example.on_safe.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.on_safe.MainActivity
import com.example.on_safe.R
import com.example.on_safe.ui.camera.CameraModeActivity

class ModeSelectActivity : AppCompatActivity() {

    private lateinit var cardGuardian: LinearLayout
    private lateinit var cardCamera: LinearLayout
    private lateinit var iconContainerGuardian: FrameLayout
    private lateinit var iconContainerCamera: FrameLayout
    private lateinit var tagContainerGuardian: ViewGroup
    private lateinit var tagContainerCamera: ViewGroup
    private lateinit var btnNext: Button

    // 0 = 미선택, 1 = 보호자, 2 = 카메라
    private var selectedMode = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mode_select)

        cardGuardian        = findViewById(R.id.cardGuardian)
        cardCamera          = findViewById(R.id.cardCamera)
        iconContainerGuardian = findViewById(R.id.iconContainerGuardian)
        iconContainerCamera   = findViewById(R.id.iconContainerCamera)
        tagContainerGuardian  = findViewById(R.id.tagContainerGuardian)
        tagContainerCamera    = findViewById(R.id.tagContainerCamera)
        btnNext             = findViewById(R.id.btnNext)

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
            // 권한 요청은 온보딩(Tutorial → Permission)에서 이미 완료됨
            // 모드 선택 후 바로 해당 모드 화면으로 진입
            val intent = when (selectedMode) {
                2 -> Intent(this, CameraModeActivity::class.java)
                else -> Intent(this, MainActivity::class.java)
            }.apply {
                putExtra("selected_mode", selectedMode)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        }
    }

    private fun updateCardState() {
        val isGuardian = selectedMode == 1

        // 카드 테두리
        cardGuardian.setBackgroundResource(
            if (isGuardian) R.drawable.bg_card_xl_selected else R.drawable.bg_card_xl
        )
        cardCamera.setBackgroundResource(
            if (!isGuardian) R.drawable.bg_card_xl_selected else R.drawable.bg_card_xl
        )

        // 아이콘 컨테이너 배경
        iconContainerGuardian.setBackgroundResource(
            if (isGuardian) R.drawable.bg_container_blue else R.drawable.bg_container_gray
        )
        iconContainerCamera.setBackgroundResource(
            if (!isGuardian) R.drawable.bg_container_blue else R.drawable.bg_container_gray
        )

        // 태그 텍스트 색상 + 배경 변경
        applyTagStyle(tagContainerGuardian, isGuardian)
        applyTagStyle(tagContainerCamera, !isGuardian)

        // 다음 버튼 활성화
        btnNext.isEnabled = true
        btnNext.alpha = 1.0f
    }

    // FlexboxLayout의 자식 TextView들에 selected 상태에 따라 pill 스타일 적용
    private fun applyTagStyle(container: ViewGroup, selected: Boolean) {
        val bgRes = if (selected) R.drawable.bg_pill_blue else R.drawable.bg_pill_gray
        val textColor = if (selected) 0xFF4D80FF.toInt() else 0xFF6B7280.toInt()
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is TextView) {
                child.setBackgroundResource(bgRes)
                child.setTextColor(textColor)
            }
        }
    }
}
