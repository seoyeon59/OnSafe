package com.example.on_safe.ui.login

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.on_safe.MainActivity
import com.example.on_safe.R
import com.example.on_safe.ui.camera.CameraModeActivity

class ModeSelectActivity : AppCompatActivity() {

    private lateinit var cardGuardian: LinearLayout
    private lateinit var cardCamera: LinearLayout
    private lateinit var iconContainerGuardian: FrameLayout
    private lateinit var iconContainerCamera: FrameLayout
    private lateinit var iconGuardian: ImageView
    private lateinit var iconCamera: ImageView
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
        iconGuardian         = findViewById(R.id.iconGuardian)
        iconCamera           = findViewById(R.id.iconCamera)
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

        // 미선택 상태에서 눌러도 반응이 없어 고장으로 보이던 문제 — 선택 전까지 비활성
        btnNext.isEnabled = false
        btnNext.alpha = 0.4f

        btnNext.setOnClickListener {
            if (selectedMode == 0) return@setOnClickListener
            // 권한 요청은 온보딩(Tutorial → Permission)에서 완료 — 바로 해당 모드로 진입
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

        // 아이콘 색상 — 선택 시 파란색, 아니면 잉크색
        val colorBlue = ContextCompat.getColor(this, R.color.primary_blue)
        val colorInk = ContextCompat.getColor(this, R.color.ink_900)
        iconGuardian.imageTintList = ColorStateList.valueOf(if (isGuardian) colorBlue else colorInk)
        iconCamera.imageTintList = ColorStateList.valueOf(if (!isGuardian) colorBlue else colorInk)

        // 태그 텍스트 색상 + 배경 변경
        applyTagStyle(tagContainerGuardian, isGuardian)
        applyTagStyle(tagContainerCamera, !isGuardian)

        // 다음 버튼 활성화
        btnNext.isEnabled = true
        btnNext.alpha = 1.0f
    }

    // FlexboxLayout 자식 TextView에 선택 상태별 pill 스타일 적용
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
