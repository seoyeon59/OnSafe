package com.example.on_safe.util

import android.content.Intent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.on_safe.MainActivity
import com.example.on_safe.R
import com.example.on_safe.ui.history.AccidentHistoryActivity
import com.example.on_safe.ui.settings.SettingsActivity
import kotlin.math.abs

/**
 * 하단 탭 바 공통 처리.
 * 세 화면에 레이아웃 100여 줄과 이동 코드가 각각 복사돼 있어 elevation·라벨 크기·활성 표시가
 * 조금씩 어긋나 있던 것을 통합. 레이아웃은 view_bottom_nav.xml 공유.
 *
 * 선언 순서 = 화면상 가로 배치 순서. 전환 방향 계산이 이 순서에 의존하므로 바꾸지 말 것.
 */
enum class NavTab(
    @IdRes internal val rootId: Int,
    @IdRes internal val pillId: Int,
    @IdRes internal val iconId: Int,
    @IdRes internal val labelId: Int,
    @DrawableRes internal val icon: Int,
    @DrawableRes internal val iconActive: Int
) {
    HISTORY(
        R.id.tabHistory, R.id.tabHistoryPill, R.id.tabHistoryIcon, R.id.tabHistoryLabel,
        R.drawable.ic_history, R.drawable.ic_history_filled
    ),
    HOME(
        R.id.tabHome, R.id.tabHomePill, R.id.tabHomeIcon, R.id.tabHomeLabel,
        R.drawable.ic_home, R.drawable.ic_home_filled
    ),
    SETTINGS(
        R.id.tabSettings, R.id.tabSettingsPill, R.id.tabSettingsIcon, R.id.tabSettingsLabel,
        R.drawable.ic_settings, R.drawable.ic_settings_filled
    )
}

/** 현재 탭을 강조하고 나머지 탭에 이동을 연결한다. */
fun AppCompatActivity.setupBottomNav(current: NavTab) {
    val activeColor = ContextCompat.getColor(this, R.color.primary_blue)
    val inactiveColor = ContextCompat.getColor(this, R.color.ink_500)

    for (tab in NavTab.values()) {
        val isCurrent = tab == current

        findViewById<View>(tab.pillId)
            .setBackgroundResource(if (isCurrent) R.drawable.bg_tab_active else 0)

        findViewById<ImageView>(tab.iconId).apply {
            setImageResource(if (isCurrent) tab.iconActive else tab.icon)
            setColorFilter(if (isCurrent) activeColor else inactiveColor)
        }

        findViewById<TextView>(tab.labelId)
            .setTextColor(if (isCurrent) activeColor else inactiveColor)

        val root = findViewById<View>(tab.rootId)
        root.isClickable = !isCurrent
        root.setOnClickListener { if (!isCurrent) moveTo(tab, current) }
    }
}

private fun AppCompatActivity.moveTo(target: NavTab, current: NavTab) {
    val destination = when (target) {
        NavTab.HISTORY -> AccidentHistoryActivity::class.java
        NavTab.HOME -> MainActivity::class.java
        NavTab.SETTINGS -> SettingsActivity::class.java
    }
    val intent = Intent(this, destination).apply {
        // 홈은 스택에 살아 있으므로 새로 쌓지 않고 기존 인스턴스로 복귀
        if (target == NavTab.HOME) {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    }
    startActivity(intent)

    // 탭의 가로 배치 순서 그대로 슬라이드 방향 결정 — 이동 방향과 화면 전환이 어긋나지 않게.
    // 한 칸 건너뛰는 이동은 중간 화면을 지나치므로 더 빠른 전환 사용.
    val skipsMiddle = abs(target.ordinal - current.ordinal) > 1
    val movingLeft = target.ordinal < current.ordinal
    when {
        movingLeft && skipsMiddle ->
            overridePendingTransition(R.anim.slide_in_left_fast, R.anim.slide_out_right_fast)
        movingLeft ->
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        skipsMiddle ->
            overridePendingTransition(R.anim.slide_in_right_fast, R.anim.slide_out_left_fast)
        else ->
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    // 홈은 뒤로가기 복귀 지점이라 유지, 나머지 탭은 정리해 스택 누적 방지
    if (current != NavTab.HOME) finish()
}
