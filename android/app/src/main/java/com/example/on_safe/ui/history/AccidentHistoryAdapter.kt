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
        private const val VIEW_TYPE_NOTICE = 2
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
        // 정렬 순서가 바뀌면 사실상 리스트 전체가 뒤집히는 수준이라, DiffUtil로 항목별
        // 이동 애니메이션을 개별 적용하면 카드들이 제각각 움직여 뒤섞인 것처럼 보인다.
        // 그래서 여기서는 통째로 다시 그려서 위에서부터 순서대로 자리 잡게 하고,
        // 스크롤 이동만으로 "정렬이 바뀌었다"는 걸 보여준다.
        replaceAllInstant(newItems)
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

    override fun getItemViewType(position: Int) = when (items[position]) {
        is HistoryListItem.DateHeader      -> VIEW_TYPE_HEADER
        is HistoryListItem.RetentionNotice -> VIEW_TYPE_NOTICE
        is HistoryListItem.HistoryEntry    -> VIEW_TYPE_ITEM
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(
                inflater.inflate(R.layout.item_date_header, parent, false)
            )
            VIEW_TYPE_NOTICE -> NoticeViewHolder(
                inflater.inflate(R.layout.item_history_notice, parent, false)
            )
            else -> ItemViewHolder(
                inflater.inflate(R.layout.item_accident_history, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is HistoryListItem.DateHeader      -> (holder as HeaderViewHolder).bind(item)
            is HistoryListItem.HistoryEntry    -> (holder as ItemViewHolder).bind(item)
            is HistoryListItem.RetentionNotice -> Unit // 고정 문구 — 바인딩할 내용 없음
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

    // 보관 기간 안내 카드 — 고정 문구만 표시, 바인딩할 동적 데이터 없음
    inner class NoticeViewHolder(view: View) : RecyclerView.ViewHolder(view)

    inner class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTime:        TextView    = view.findViewById(R.id.tvTime)
        private val btnWatchVideo: LinearLayout = view.findViewById(R.id.btnWatchVideo)
        private val btnDownload:   ImageButton  = view.findViewById(R.id.btnDownload)
        private val btnDelete:     ImageButton  = view.findViewById(R.id.btnDelete)

        fun bind(entry: HistoryListItem.HistoryEntry) {
            // 영상 길이로 오해되지 않도록 "감지 시각" 접두어 부여 (알림 상세 모달과 동일한 표현으로 통일)
            tvTime.text = "감지 시각 · ${entry.time}"
            // 모든 항목이 낙상(FALL)이므로 카드 좌측 상단에 고정 표시 (배지 제거)

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
            // 오래된순: 가장 오래된 항목이 리스트 맨 위에 오므로, 안내 문구도 맨 위에 먼저 배치
            if (sortOrder == SortOrder.OLDEST_FIRST) add(HistoryListItem.RetentionNotice)

            sorted.groupBy { it.date }
                .forEach { (date, group) ->
                    add(HistoryListItem.DateHeader(date))
                    addAll(group)
                }

            // 최신순: 가장 오래된 항목이 리스트 맨 아래에 오므로, 안내 문구도 맨 아래에 배치
            if (sortOrder == SortOrder.NEWEST_FIRST) add(HistoryListItem.RetentionNotice)
        }
    }

    private fun applyDiff(newItems: List<HistoryListItem>) {
        val diffResult = DiffUtil.calculateDiff(HistoryDiffCallback(items, newItems))
        items.clear()
        items.addAll(newItems)
        diffResult.dispatchUpdatesTo(this)
    }

    // 삭제(removeItem)처럼 항목 한두 개만 바뀌는 경우가 아니라 정렬 전체가 뒤바뀌는 경우 전용 —
    // 개별 이동 애니메이션 없이 한 번에 새로 그린다
    private fun replaceAllInstant(newItems: List<HistoryListItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
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
                old is HistoryListItem.DateHeader      && new is HistoryListItem.DateHeader      -> old.date == new.date
                old is HistoryListItem.HistoryEntry    && new is HistoryListItem.HistoryEntry    -> old.id == new.id
                old is HistoryListItem.RetentionNotice && new is HistoryListItem.RetentionNotice  -> true
                else -> false
            }
        }

        override fun areContentsTheSame(oldPos: Int, newPos: Int) =
            oldList[oldPos] == newList[newPos]
    }
}
