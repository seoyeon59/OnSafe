package com.example.on_safe.util

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.on_safe.R

/**
 * 위험 지수 점수 카드 바인딩 (MainActivity / NotificationActivity 공용).
 *
 * @param cardRoot include_risk_score_card 레이아웃을 포함하는 루트 뷰
 */
object RiskScoreCardBinder {

    private const val DANGER_BORDER_DP = 2f

    enum class RiskLevel(
        val label: String,
        val rangeText: String,
        val message: String,
        val colorRes: Int
    ) {
        NORMAL ("정상", "정상 구간 0~50",   "안정적인 상태입니다.",                 R.color.status_normal),
        WARNING("주의", "주의 구간 51~75",  "움직임에 주의가 필요합니다.",            R.color.status_warning),
        DANGER ("위험", "위험 구간 76~100", "낙상이 의심됩니다. 즉시 확인이 필요합니다.", R.color.status_danger);

        companion object {
            fun fromScore(score: Int): RiskLevel = when {
                score <= 50 -> NORMAL
                score <= 75 -> WARNING
                else        -> DANGER
            }
        }
    }

    /**
     * 점수 미수신 상태 표시.
     * 레이아웃 기본값에 맡길 경우 데이터 없이 "정상"으로 보이는 오표시 발생.
     */
    fun bindUnknown(cardRoot: View, message: String) {
        val color = ContextCompat.getColor(cardRoot.context, R.color.ink_500)
        applyText(cardRoot, DisplayText.NO_SCORE, DisplayText.UNKNOWN_LEVEL, "", message, color)
        applyProgress(cardRoot, ratio = 0f, color = color)
        // 이전 DANGER 테두리 잔존 방지용 해제
        applyDangerBorder(cardRoot, danger = false, color = color)
    }

    fun bind(cardRoot: View, score: Int) {
        val level = RiskLevel.fromScore(score)
        val color = ContextCompat.getColor(cardRoot.context, level.colorRes)
        applyText(cardRoot, score.toString(), level.label, level.rangeText, level.message, color)
        applyProgress(cardRoot, score.coerceIn(0, 100) / 100f, color)
        applyDangerBorder(cardRoot, danger = level == RiskLevel.DANGER, color = color)
    }

    private fun applyText(
        cardRoot: View,
        score: String,
        badge: String,
        range: String,
        message: String,
        color: Int
    ) {
        cardRoot.findViewById<TextView>(R.id.tvRiskScore).also {
            it.text = score
            it.setTextColor(color)
        }
        cardRoot.findViewById<TextView>(R.id.tvRiskStatusBadge).also {
            it.text = badge
            it.backgroundTintList = ColorStateList.valueOf(color)
        }
        cardRoot.findViewById<TextView>(R.id.tvRiskRange).text = range
        cardRoot.findViewById<TextView>(R.id.tvRiskMessage).text = message
    }

    private fun applyProgress(cardRoot: View, ratio: Float, color: Int) {
        val fill = cardRoot.findViewById<View>(R.id.progressFill)
        fill.backgroundTintList = ColorStateList.valueOf(color)

        val container = fill.parent as? FrameLayout
        if (container == null || ratio <= 0f) {
            fill.setWidth(0)
            return
        }
        // 컨테이너 폭은 레이아웃이 끝나야 확정 — post로 지연
        container.post { fill.setWidth((container.width * ratio).toInt()) }
    }

    /**
     * DANGER 단계만 빨간 테두리, 그 외에는 제거.
     * mutate()로 이 카드 전용 Drawable 사본을 만들어 다른 카드 배경에 영향 없도록 함.
     */
    private fun applyDangerBorder(cardRoot: View, danger: Boolean, color: Int) {
        val cardBg = cardRoot.background?.mutate() as? GradientDrawable ?: return
        if (danger) {
            val density = cardRoot.resources.displayMetrics.density
            cardBg.setStroke((DANGER_BORDER_DP * density).toInt(), color)
        } else {
            cardBg.setStroke(0, 0)
        }
    }

    private fun View.setWidth(px: Int) {
        layoutParams = layoutParams.also { it.width = px }
    }
}
