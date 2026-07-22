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
 * - 위험(FALL) 항목만 표시 (주의 타입은 저장 안 함)
 * - DiffUtil로 최소 변경만 반영
 * - 날짜 헤더 / 이력 아이템 두 가지 ViewType 사용
 */
class AccidentHistoryAdapter(
    private val onWatchVideo: (HistoryListItem.HistoryEntry) -> Unit,
    private val onDownload:   (HistoryListItem.HistoryEntry) -> Unit,
    private val onDelete:     (HistoryListItem.HistoryEntry) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<HistoryListItem>()
    private var currentSortOrder: SortOrder = SortOrder.NEWEST_FIRST

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM   = 1
    }

    // ──────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────

    fun submitList(
        rawEntries: List<HistoryListItem.HistoryEntry>,
        sortOrder: SortOrder = currentSortOrder
    ) {
        currentSortOrder = sortOrder
        // 위험(FALL) 항목만 표시
        val filtered = rawEntries.filter { it.type == HistoryType.FALL }
        val newItems = buildSectionedList(filtered, sortOrder)
        applyDiff(newItems)
    }

    fun removeItem(id: String) {
        val updated = items
            .filterIsInstance<HistoryListItem.HistoryEntry>()
            .filter { it.id != id }
        val newItems = buildSectionedList(updated, currentSortOrder)
        applyDiff(newItems)
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
            is HistoryListItem.DateHeader   -> (holder as HeaderViewHolder).bind(item)
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
        private val tvStatusBadge: TextView    = view.findViewById(R.id.tvStatusBadge)
        private val tvTime:        TextView    = view.findViewById(R.id.tvTime)
        private val btnWatchVideo: LinearLayout = view.findViewById(R.id.btnWatchVideo)
        private val btnDownload:   ImageButton  = view.findViewById(R.id.btnDownload)
        private val btnDelete:     ImageButton  = view.findViewById(R.id.btnDelete)

        fun bind(entry: HistoryListItem.HistoryEntry) {
            tvTime.text = entry.time
            // 위험 배지 (항상 FALL 타입만 표시되므로 고정)
            tvStatusBadge.text = "위험"
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_danger)

            btnWatchVideo.setOnClickListener { onWatchVideo(entry) }
            btnDownload.setOnClickListener   { onDownload(entry) }
            btnDelete.setOnClickListener     { onDelete(entry) }
        }
    }

    // ──────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────

    /**
     * 항목 리스트를 날짜별로 그룹화하여 DateHeader + HistoryEntry 형태로 변환.
     * sortOrder에 따라 날짜 및 시간 정렬 방향이 달라진다.
     */
    private fun buildSectionedList(
        entries: List<HistoryListItem.HistoryEntry>,
        sortOrder: SortOrder
    ): List<HistoryListItem> {
        if (entries.isEmpty()) return emptyList()

        val sorted = when (sortOrder) {
            SortOrder.NEWEST_FIRST ->
                entries.sortedWith(
                    compareByDescending<HistoryListItem.HistoryEntry> { it.date }
                        .thenByDescending { it.time }
                )
            SortOrder.OLDEST_FIRST ->
                entries.sortedWith(
                    compareBy<HistoryListItem.HistoryEntry> { it.date }
                        .thenBy { it.time }
                )
        }

        return buildList {
            sorted.groupBy { it.date }
                .forEach { (date, group) ->
                    add(HistoryListItem.DateHeader(date))
                    addAll(group)
                }
        }
    }

    private fun applyDiff(newItems: List<HistoryListItem>) {
        val diffResult = DiffUtil.calculateDiff(HistoryDiffCallback(items, newItems))
        items.clear()
        items.addAll(newItems)
        diffResult.dispatchUpdatesTo(this)
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
                old is HistoryListItem.DateHeader   && new is HistoryListItem.DateHeader   -> old.date == new.date
                old is HistoryListItem.HistoryEntry && new is HistoryListItem.HistoryEntry -> old.id == new.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldPos: Int, newPos: Int) =
            oldList[oldPos] == newList[newPos]
    }
}
