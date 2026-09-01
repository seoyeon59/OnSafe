package com.example.on_safe.ui.history

// 사고 이력 유형
enum class HistoryType {
    FALL,    // 위험 (낙상 감지)
    WARNING  // 주의
}

// RecyclerView 아이템 타입 (날짜 헤더 / 이력 항목 / 보관 안내)
// DiffUtil의 equals·hashCode 활용 목적의 data class
sealed class HistoryListItem {

    data class DateHeader(
        val date: String
    ) : HistoryListItem()

    data class HistoryEntry(
        val id: String,
        val type: HistoryType,
        val time: String,
        val date: String,
        val hasVideo: Boolean = false,
        // none(영상 미제공) / processing(준비 중) / ready(hasVideo와 동일 조건)
        val videoStatus: String = "none"
    ) : HistoryListItem()

    // 보관 기간 안내 — 목록의 "가장 오래된 데이터" 쪽 끝에 붙는 카드
    // 최신순: 맨 아래 / 오래된순: 맨 위
    object RetentionNotice : HistoryListItem()
}

// 정렬 순서 (최신순 기본)
enum class SortOrder { NEWEST_FIRST, OLDEST_FIRST }
