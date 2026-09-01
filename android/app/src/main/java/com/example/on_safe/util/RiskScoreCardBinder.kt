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

    enum class RiskLevel(
        val label: String,
        val rangeText: String,
        val message: String,
        val colorRes: Int
    ) {
        NORMAL ("정상", "정상 구간 0~50",   "안정적인 상태입니다.",             R.color.status_normal),
        WARNING("주의", "주의 구간 51~75",  "움직임에 주의가 필요합니다.",        R.color.status_warning),
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

        cardRoot.findViewById<TextView>(R.id.tvRiskScore).also {
            it.text = DisplayText.NO_SCORE
            it.setTextColor(color)
        }
        cardRoot.findViewById<TextView>(R.id.tvRiskStatusBadge).also {
            it.text = DisplayText.UNKNOWN_LEVEL
            it.backgroundTintList = ColorStateList.valueOf(color)
        }
        cardRoot.findViewById<TextView>(R.id.tvRiskRange).text = ""
        cardRoot.findViewById<TextView>(R.id.tvRiskMessage).text = message

        cardRoot.findViewById<View>(R.id.progressFill).also { fill ->
            fill.layoutParams = fill.layoutParams.also { it.width = 0 }
            fill.backgroundTintList = ColorStateList.valueOf(color)
        }

        // 이전 DANGER 테두리 잔존 방지용 해제
        (cardRoot.background?.mutate() as? GradientDrawable)?.setStroke(0, 0)
    }

    fun bind(cardRoot: View, score: Int) {
        val context = cardRoot.context
        val level   = RiskLevel.fromScore(score)
        val color   = ContextCompat.getColor(context, level.colorRes)
        val density = context.resources.displayMetrics.density

        cardRoot.findViewById<TextView>(R.id.tvRiskScore).also {
            it.text = score.toString()
            it.setTextColor(color)
        }
        cardRoot.findViewById<TextView>(R.id.tvRiskStatusBadge).also {
            it.text = level.label
            it.backgroundTintList = ColorStateList.valueOf(color)
        }
        cardRoot.findViewById<TextView>(R.id.tvRiskRange).text    = level.rangeText
        cardRoot.findViewById<TextView>(R.id.tvRiskMessage).text  = level.message

        val progressFill      = cardRoot.findViewById<View>(R.id.progressFill)
        val progressContainer = progressFill.parent as? FrameLayout ?: return
        progressContainer.post {
            val ratio = score.coerceIn(0, 100) / 100f
            progressFill.layoutParams = progressFill.layoutParams.also {
                it.width = (progressContainer.width * ratio).toInt()
            }
            progressFill.backgroundTintList = ColorStateList.valueOf(color)
        }

        // DANGER 단계이면 카드 테두리 빨강, 아니면 제거
        // mutate()로 이 카드 전용 Drawable 사본을 만들어 다른 카드 배경에 영향 없도록 함
        val cardBg = cardRoot.background?.mutate() as? GradientDrawable ?: return
        if (level == RiskLevel.DANGER) {
            cardBg.setStroke((2 * density).toInt(), color)
        } else {
            cardBg.setStroke(0, 0)
        }
    }
}
