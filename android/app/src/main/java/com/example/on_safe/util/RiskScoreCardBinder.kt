package com.example.on_safe.util

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.on_safe.R

/**
 * 위험 지수 점수 카드 뷰 바인딩 유틸리티
 * MainActivity / NotificationActivity 에서 공통으로 사용
 *
 * @param cardRoot  tvRiskScore, tvRiskStatusBadge, tvRiskRange, tvRiskMessage,
 *                  progressFill 을 포함하는 카드 루트 뷰
 *                  (예: R.id.riskScoreCard 또는 R.id.alertRiskScoreCard)
 */
object RiskScoreCardBinder {

    enum class RiskLevel(
        val label: String,
        val rangeText: String,
        val message: String,
        val colorRes: Int
    ) {
        NORMAL ("정상", "위험 지수 0~50",   "안정적인 상태입니다.",             R.color.status_normal),
        WARNING("주의", "위험 지수 51~75",  "움직임에 주의가 필요합니다.",        R.color.status_warning),
        DANGER ("위험", "위험 지수 76~100", "낙상이 의심됩니다. 즉시 확인이 필요합니다.", R.color.status_danger);

        companion object {
            fun fromScore(score: Int): RiskLevel = when {
                score <= 50 -> NORMAL
                score <= 75 -> WARNING
                else        -> DANGER
            }
        }
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
        val progressContainer = progressFill.parent as FrameLayout
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
