package com.example.on_safe.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.on_safe.R

/**
 * 사고 이력 RecyclerView 어댑터.
 *
 * 성능 고려사항:
 * - DiffUtil로 최소 변경만 반영 (전체 notifyDataSetChanged 지양)
 * - ViewHolder 패턴으로 View 재생성 최소화
 * - 배경 drawable은 mutate() 없이 backgroundTintList로 관리
 * - 날짜 헤더 / 이력 아이템 두 가지 ViewType 사용
 */
class AccidentHistoryAdapter(
    private val onWatchVideo: (HistoryListItem.HistoryEntry) -> Unit,
    private val onDownload:   (HistoryListItem.HistoryEntry) -> Unit,
    private val onDelete:     (HistoryListItem.HistoryEntry) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<HistoryListItem>()

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM   = 1
    }

    // ──────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────

    /**
     * 전체 raw 데이터에서 필터를 적용하고 날짜 헤더를 삽입한 뒤
     * DiffUtil로 최소 변경만 RecyclerView에 반영한다.
     */
    fun submitFilteredList(rawEntries: List<HistoryListItem.HistoryEntry>, filter: FilterType) {
        val filtered = when (filter) {
            FilterType.ALL     -> rawEntries
            FilterType.FALL    -> rawEntries.filter { it.type == HistoryType.FALL }
            FilterType.WARNING -> rawEntries.filter { it.type == HistoryType.WARNING }
        }

        val newItems = buildSectionedList(filtered)
        val diffResult = DiffUtil.calculateDiff(HistoryDiffCallback(items, newItems))
        items.clear()
        items.addAll(newItems)
        diffResult.dispatchUpdatesTo(this)
    }

    /**
     * 특정 id 항목을 목록에서 제거하고 빈 날짜 헤더도 정리한다.
     * O(n)이지만 최신 이력 수준에서는 충분히 빠르다.
     */
    fun removeItem(id: String) {
        val updated = items
            .filterIsInstance<HistoryListItem.HistoryEntry>()
            .filter { it.id != id }
        val newItems = buildSectionedList(updated)
        val diffResult = DiffUtil.calculateDiff(HistoryDiffCallback(items, newItems))
        items.clear()
        items.addAll(newItems)
        diffResult.dispatchUpdatesTo(this)
    }

    fun isEmpty(): Boolean = items.isEmpty()

    // ──────────────────────────────────────────────
    // RecyclerView.Adapter overrides
    // ──────────────────────────────────────────────

    override fun getItemViewType(position: Int) =
        if (items[position] is HistoryListItem.DateHeader) VIEW_TYPE_HEADER else VIEW_TYPE_ITEM

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(
                inflater.inflate(R.layout.item_date_header, parent, false)
            )
            else -> ItemViewHolder(
                inflater.inflate(R.layout.item_accident_history, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is HistoryListItem.DateHeader  -> (holder as HeaderViewHolder).bind(item)
            is HistoryListItem.HistoryEntry -> (holder as ItemViewHolder).bind(item)
        }
    }

    // ──────────────────────────────────────────────
    // ViewHolders
    // ──────────────────────────────────────────────

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvDate: TextView = view.findViewById(R.id.tvDate)
        fun bind(header: HistoryListItem.DateHeader) {
            tvDate.text = header.date
        }
    }

    inner class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivThumbnail:  ImageView   = view.findViewById(R.id.ivThumbnail)
        private val tvStatusBadge: TextView   = view.findViewById(R.id.tvStatusBadge)
        private val tvTime:        TextView   = view.findViewById(R.id.tvTime)
        private val btnWatchVideo: LinearLayout = view.findViewById(R.id.btnWatchVideo)
        private val btnDownload:   ImageButton  = view.findViewById(R.id.btnDownload)
        private val btnDelete:     ImageButton  = view.findViewById(R.id.btnDelete)

        fun bind(entry: HistoryListItem.HistoryEntry) {
            tvTime.text = entry.time

            // 배지 색상 + 텍스트
            when (entry.type) {
                HistoryType.FALL -> {
                    tvStatusBadge.text = "위험"
                    tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_danger)
                }
                HistoryType.WARNING -> {
                    tvStatusBadge.text = "주의"
                    tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_warning)
                }
            }

            // 썸네일 (현재는 placeholder; 나중에 Glide/Coil 등으로 교체)
            ivThumbnail.setImageDrawable(null)

            btnWatchVideo.setOnClickListener { onWatchVideo(entry) }
            btnDownload.setOnClickListener   { onDownload(entry) }
            btnDelete.setOnClickListener     { onDelete(entry) }
        }
    }

    // ──────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────

    /**
     * 항목 리스트를 날짜별로 그룹화하여 DateHeader + HistoryEntry 형태로 변환한다.
     * 날짜 내림차순 정렬 후 항목 내 시간 내림차순 정렬.
     */
    private fun buildSectionedList(
        entries: List<HistoryListItem.HistoryEntry>
    ): List<HistoryListItem> {
        if (entries.isEmpty()) return emptyList()

        return buildList {
            entries
                .sortedWith(compareByDescending<HistoryListItem.HistoryEntry> { it.date }
                    .thenByDescending { it.time })
                .groupBy { it.date }
                .forEach { (date, group) ->
                    add(HistoryListItem.DateHeader(date))
                    addAll(group)
                }
        }
    }

    // ──────────────────────────────────────────────
    // DiffUtil
    // ──────────────────────────────────────────────

    private class HistoryDiffCallback(
        private val oldList: List<HistoryListItem>,
        private val newList: List<HistoryListItem>
    ) : DiffUtil.Callback() {

        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
            val old = oldList[oldPos]
            val new = newList[newPos]
            return when {
                old is HistoryListItem.DateHeader  && new is HistoryListItem.DateHeader  -> old.date == new.date
                old is HistoryListItem.HistoryEntry && new is HistoryListItem.HistoryEntry -> old.id == new.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldPos: Int, newPos: Int) =
            oldList[oldPos] == newList[newPos]
    }
}
