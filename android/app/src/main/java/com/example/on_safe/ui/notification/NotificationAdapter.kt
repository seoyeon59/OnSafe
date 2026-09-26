package com.example.on_safe.ui.notification

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.on_safe.R

// 알림 종류별 표시 규칙 — 어댑터 분기 대신 값으로 보유
enum class NotificationType(
    val iconRes: Int,
    val iconTintRes: Int,      // 아이콘·위험 지수 색
    val circleTintRes: Int,    // 아이콘 원형 배경 색
    val clickable: Boolean
) {
    // 낙상 위험 감지 — 화살표 + 클릭 시 모달
    FALL(R.drawable.ic_siren, R.color.status_danger, R.color.tint_danger, clickable = true),

    // 주의 상태 감지 — 클릭 없음
    WARNING(R.drawable.ic_warning, R.color.status_warning, R.color.tint_warning, clickable = false),

    // 페어링·오프라인 등 시스템 알림 — 위험 지수 대신 본문 표시
    SYSTEM(R.drawable.ic_notification, R.color.ink_500, R.color.tint_neutral, clickable = false)
}

data class NotificationItem(
    val id: String,              // 서버 notificationId — 읽음 처리용
    val logId: String?,          // 낙상 계열의 fall_logs id — 사고 처리용. 시스템 알림은 null
    val type: NotificationType,
    val title: String,
    val body: String,            // 서버 본문. SYSTEM만 표시
    val time: String,            // 표시용 문자열 (예: "오늘 · 오후 02:23")
    val riskScore: Int,          // 낙상·주의만 유효(0..100). SYSTEM은 0
    val detectedAtMillis: Long,  // 모달에 감지 시각 표시용
    val isUnread: Boolean = false
)

// 목록 상태는 NotificationViewModel이 소유하고, 어댑터는 submitList()로 받아 그리기만 한다
class NotificationAdapter(
    private val onFallItemClick: (item: NotificationItem) -> Unit
) : ListAdapter<NotificationItem, NotificationAdapter.ViewHolder>(DIFF) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val flIconCircle: View = view.findViewById(R.id.fl_icon_circle)
        val ivIcon: ImageView = view.findViewById(R.id.iv_notification_icon)
        val tvBody: TextView = view.findViewById(R.id.tv_body)
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
        val item = getItem(position)
        val ctx = holder.itemView.context
        val type = item.type

        holder.tvTitle.text = item.title
        holder.tvTime.text = item.time
        // 시스템 알림은 본문, 낙상·주의는 위험 지수
        val isSystem = type == NotificationType.SYSTEM
        holder.tvBody.isVisible = isSystem
        holder.tvBody.text = item.body
        holder.tvRiskScore.isVisible = !isSystem
        holder.tvRiskScore.text = "위험 지수 ${item.riskScore}"
        holder.viewUnreadDot.isVisible = item.isUnread

        val iconTint = ContextCompat.getColorStateList(ctx, type.iconTintRes)
        holder.ivIcon.setImageResource(type.iconRes)
        holder.ivIcon.imageTintList = iconTint
        holder.tvRiskScore.setTextColor(iconTint)
        holder.flIconCircle.backgroundTintList = ContextCompat.getColorStateList(ctx, type.circleTintRes)

        // 재사용된 뷰에 이전 종류의 리스너가 남지 않도록 두 경우 모두 명시 지정
        holder.ivArrow.isVisible = type.clickable
        if (type.clickable) {
            holder.itemView.setOnClickListener {
                // bindingAdapterPosition은 RecyclerView 1.2.0+ — 현재 해석 버전 1.1.0.
                // 중첩·concat 어댑터가 없어 adapterPosition과 동작 동일.
                val pos = holder.adapterPosition
                if (pos != RecyclerView.NO_POSITION) onFallItemClick(getItem(pos))
            }
        } else {
            holder.itemView.setOnClickListener(null)
            holder.itemView.isClickable = false
        }
    }

    private companion object {
        // id 기준 비교 — 읽음 처리 시 해당 행만 다시 그림
        val DIFF = object : DiffUtil.ItemCallback<NotificationItem>() {
            override fun areItemsTheSame(oldItem: NotificationItem, newItem: NotificationItem) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: NotificationItem, newItem: NotificationItem) =
                oldItem == newItem
        }
    }
}
