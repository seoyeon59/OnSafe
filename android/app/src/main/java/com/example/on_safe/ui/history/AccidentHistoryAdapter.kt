package com.example.on_safe.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.on_safe.R

/**
 * 사고 이력 RecyclerView 어댑터.
 * - 위험(FALL) 항목만 표시
 * - DiffUtil 미사용 — 정렬 변경 시 이동 애니메이션이 뒤섞이는 문제로 매번 전체 재그리기
 * - 날짜 헤더 / 이력 / 보관 안내 3가지 ViewType
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
        // 정렬 변경은 목록 전체가 뒤집히는 수준 — 통째로 재그리기 (클래스 주석 참고)
        replaceAllInstant(newItems)
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

    // 보관 기간 안내 — 고정 문구, 바인딩 불필요
    inner class NoticeViewHolder(view: View) : RecyclerView.ViewHolder(view)

    inner class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTime:        TextView    = view.findViewById(R.id.tvTime)
        private val btnWatchVideo: LinearLayout = view.findViewById(R.id.btnWatchVideo)
        private val btnDownload:   ImageButton  = view.findViewById(R.id.btnDownload)
        private val btnDelete:     ImageButton  = view.findViewById(R.id.btnDelete)

        fun bind(entry: HistoryListItem.HistoryEntry) {
            // 영상 길이 오해 방지용 "감지 시각" 접두어 — 알림 상세 모달과 표현 통일
            tvTime.text = "감지 시각 · ${entry.time}"

            btnWatchVideo.setOnClickListener { onWatchVideo(entry) }
            btnDownload.setOnClickListener   { onDownload(entry) }
            btnDelete.setOnClickListener     { onDelete(entry) }
        }
    }

    // ──────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────

    // 날짜별 그룹화 → DateHeader + HistoryEntry 변환. 정렬 방향은 sortOrder 기준.
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
            // 오래된순: 가장 오래된 항목이 맨 위 → 안내도 맨 위
            if (sortOrder == SortOrder.OLDEST_FIRST) add(HistoryListItem.RetentionNotice)

            sorted.groupBy { it.date }
                .forEach { (date, group) ->
                    add(HistoryListItem.DateHeader(date))
                    addAll(group)
                }

            // 최신순: 가장 오래된 항목이 맨 아래 → 안내도 맨 아래
            if (sortOrder == SortOrder.NEWEST_FIRST) add(HistoryListItem.RetentionNotice)
        }
    }

    private fun replaceAllInstant(newItems: List<HistoryListItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
