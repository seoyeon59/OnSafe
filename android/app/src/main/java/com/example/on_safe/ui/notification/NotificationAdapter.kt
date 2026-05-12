package com.example.on_safe.ui.notification

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.on_safe.R

// ─── 알림 타입 ────────────────────────────────────────────────
enum class NotificationType {
    FALL,    // 낙상 위험 감지 (빨강) → 화살표 + 터치 모달
    WARNING  // 주의 상태 감지 (노랑) → 알림 전용, 터치 없음
}

// ─── 알림 데이터 클래스 ────────────────────────────────────────
data class NotificationItem(
    val type: NotificationType,
    val title: String,
    val time: String,
    val riskScore: Int,
    val isUnread: Boolean = false  // 새 알림만 true
)

// ─── 어댑터 ───────────────────────────────────────────────────
class NotificationAdapter(
    private val items: MutableList<NotificationItem>,
    private val onFallItemClick: (NotificationItem) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.iv_notification_icon)
        val tvTitle: TextView = view.findViewById(R.id.tv_title)
        val tvTime: TextView = view.findViewById(R.id.tv_time)
        val tvRiskScore: TextView = view.findViewById(R.id.tv_risk_score)
        val viewUnreadDot: View = view.findViewById(R.id.view_unread_dot)
        val ivArrow: ImageView = view.findViewById(R.id.iv_arrow)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context

        holder.tvTitle.text = item.title
        holder.tvTime.text = item.time
        holder.tvRiskScore.text = "위험 지수 ${item.riskScore}"

        // 새 알림만 파란 점
        holder.viewUnreadDot.visibility = if (item.isUnread) View.VISIBLE else View.GONE

        when (item.type) {
            NotificationType.FALL -> {
                holder.ivIcon.setImageResource(R.drawable.ic_siren)
                holder.ivIcon.imageTintList =
                    ContextCompat.getColorStateList(ctx, android.R.color.holo_red_light)
                holder.tvRiskScore.setTextColor(
                    ContextCompat.getColor(ctx, R.color.status_danger)
                )
                // 화살표 보이고, 터치 시 모달
                holder.ivArrow.visibility = View.VISIBLE
                holder.itemView.setOnClickListener { onFallItemClick(item) }
            }
            NotificationType.WARNING -> {
                holder.ivIcon.setImageResource(R.drawable.ic_warning)
                holder.ivIcon.imageTintList =
                    ContextCompat.getColorStateList(ctx, android.R.color.holo_orange_light)
                holder.tvRiskScore.setTextColor(
                    ContextCompat.getColor(ctx, R.color.status_warning)
                )
                // 화살표 숨기고, 터치 없음
                holder.ivArrow.visibility = View.GONE
                holder.itemView.setOnClickListener(null)
            }
        }
    }

    override fun getItemCount() = items.size

    // API 연동 시 호출
    fun updateItems(newItems: List<NotificationItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}