package com.example.on_safe.ui.login

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.on_safe.MainActivity
import com.example.on_safe.R
import com.example.on_safe.ui.camera.CameraModeActivity
import com.example.on_safe.util.setEnabledWithAlpha

class ModeSelectActivity : AppCompatActivity() {

    private enum class Mode { GUARDIAN, CAMERA }

    // 카드 한 장 — 선택 상태에 따라 테두리·아이콘·태그 스타일이 함께 바뀜
    private class ModeCard(
        val root: View,
        val iconContainer: View,
        val icon: ImageView,
        val tagContainer: ViewGroup
    )

    private lateinit var guardianCard: ModeCard
    private lateinit var cameraCard: ModeCard
    private lateinit var btnNext: Button

    private var selectedMode: Mode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mode_select)

        guardianCard = ModeCard(
            root = findViewById(R.id.cardGuardian),
            iconContainer = findViewById(R.id.iconContainerGuardian),
            icon = findViewById(R.id.iconGuardian),
            tagContainer = findViewById(R.id.tagContainerGuardian)
        )
        cameraCard = ModeCard(
            root = findViewById(R.id.cardCamera),
            iconContainer = findViewById(R.id.iconContainerCamera),
            icon = findViewById(R.id.iconCamera),
            tagContainer = findViewById(R.id.tagContainerCamera)
        )
        btnNext = findViewById(R.id.btnNext)

        guardianCard.root.setOnClickListener { selectMode(Mode.GUARDIAN) }
        cameraCard.root.setOnClickListener { selectMode(Mode.CAMERA) }

        // 미선택 상태에서 눌러도 반응이 없어 고장으로 보이던 문제 — 선택 전까지 비활성
        btnNext.setEnabledWithAlpha(false)

        btnNext.setOnClickListener {
            val mode = selectedMode ?: return@setOnClickListener
            // 권한 요청은 온보딩(Tutorial → Permission)에서 완료 — 바로 해당 모드로 진입
            val target = when (mode) {
                Mode.CAMERA -> CameraModeActivity::class.java
                Mode.GUARDIAN -> MainActivity::class.java
            }
            startActivity(
                Intent(this, target).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
        }
    }

    private fun selectMode(mode: Mode) {
        selectedMode = mode
        applyCardState(guardianCard, mode == Mode.GUARDIAN)
        applyCardState(cameraCard, mode == Mode.CAMERA)
        btnNext.setEnabledWithAlpha(true)
    }

    private fun applyCardState(card: ModeCard, selected: Boolean) {
        card.root.setBackgroundResource(
            if (selected) R.drawable.bg_card_xl_selected else R.drawable.bg_card_xl
        )
        card.iconContainer.setBackgroundResource(
            if (selected) R.drawable.bg_container_blue else R.drawable.bg_container_gray
        )
        // 아이콘 색상 — 선택 시 파란색, 아니면 잉크색
        val iconColor = ContextCompat.getColor(
            this, if (selected) R.color.primary_blue else R.color.ink_900
        )
        card.icon.imageTintList = ColorStateList.valueOf(iconColor)
        applyTagStyle(card.tagContainer, selected)
    }

    // FlexboxLayout 자식 TextView에 선택 상태별 pill 스타일 적용
    private fun applyTagStyle(container: ViewGroup, selected: Boolean) {
        val bgRes = if (selected) R.drawable.bg_pill_blue else R.drawable.bg_pill_gray
        val textColor = if (selected) TAG_TEXT_SELECTED else TAG_TEXT_NORMAL
        for (i in 0 until container.childCount) {
            (container.getChildAt(i) as? TextView)?.apply {
                setBackgroundResource(bgRes)
                setTextColor(textColor)
            }
        }
    }

    private companion object {
        const val TAG_TEXT_SELECTED = 0xFF4D80FF.toInt()
        const val TAG_TEXT_NORMAL = 0xFF6B7280.toInt()
    }
}
