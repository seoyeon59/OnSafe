package com.example.on_safe.ui.notification

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.on_safe.R


enum class NotificationType {
    FALL,    // 낙상 위험 감지 (빨강)
    WARNING  // 주의 상태 감지 (노랑)
}

// ─── 알림 데이터 클래스 ────────────────────────────────────────
data class NotificationItem(
    val type: NotificationType,
    val title: String,
    val time: String,           // 표시용 문자열 (예: "오늘 · 오후 02:23")
    val riskScore: Int,
    val detectedAtMillis: Long, // 모달에 감지 시각 표시용 (API 연동 시 서버 timestamp 사용)
    val isUnread: Boolean = false
)

// ─── 어댑터 ───────────────────────────────────────────────────
// onFallItemClick: FALL 아이템 클릭 시 호출 (position, item 전달)
class NotificationAdapter(
    private val items: MutableList<NotificationItem>,
    private val onFallItemClick: (position: Int, item: NotificationItem) -> Unit
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
        holder.viewUnreadDot.visibility = if (item.isUnread) View.VISIBLE else View.GONE

        when (item.type) {
            NotificationType.FALL -> {
                holder.ivIcon.setImageResource(R.drawable.ic_siren)
                holder.ivIcon.imageTintList =
                    ContextCompat.getColorStateList(ctx, android.R.color.holo_red_light)
                holder.tvRiskScore.setTextColor(
                    ContextCompat.getColor(ctx, R.color.status_danger)
                )
                // FALL: 화살표 표시 + 클릭 시 모달
                holder.ivArrow.visibility = View.VISIBLE
                holder.itemView.setOnClickListener {
                    val pos = holder.adapterPosition
                    if (pos != RecyclerView.NO_POSITION) onFallItemClick(pos, items[pos])
                }
            }
            NotificationType.WARNING -> {
                holder.ivIcon.setImageResource(R.drawable.ic_warning)
                holder.ivIcon.imageTintList =
                    ContextCompat.getColorStateList(ctx, android.R.color.holo_orange_light)
                holder.tvRiskScore.setTextColor(
                    ContextCompat.getColor(ctx, R.color.status_warning)
                )
                // WARNING: 화살표 없음, 클릭 없음
                holder.ivArrow.visibility = View.GONE
                holder.itemView.setOnClickListener(null)
                holder.itemView.isClickable = false
            }
        }
    }

    override fun getItemCount() = items.size

    // FALL 모달에서 확인/119 후 호출 — 해당 항목 읽음 처리
    fun markAsRead(position: Int) {
        val item = items.getOrNull(position) ?: return
        if (!item.isUnread) return
        items[position] = item.copy(isUnread = false)
        notifyItemChanged(position)
    }

    // 화면 진입 시 WARNING 전체 일괄 읽음 처리
    // 바뀐 항목만 notifyItemChanged로 갱신 (전체 리스트를 다시 그리지 않아 깜빡임 없음)
    fun markAllWarningsAsRead() {
        items.forEachIndexed { index, item ->
            if (item.type == NotificationType.WARNING && item.isUnread) {
                items[index] = item.copy(isUnread = false)
                notifyItemChanged(index)
            }
        }
    }

    fun hasUnreadItems(): Boolean = items.any { it.isUnread }

    // TODO: GET /notification/list API 연동 시 호출
    fun updateItems(newItems: List<NotificationItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}