package com.example.on_safe.ui.history

/**
 * 사고 이력 유형
 */
enum class HistoryType {
    FALL,    // 위험 (낙상 감지)
    WARNING  // 주의
}

/**
 * RecyclerView에 표시되는 아이템 타입 (날짜 헤더 / 이력 항목)
 * DiffUtil에 사용하기 위해 equals/hashCode 기반의 data class 사용
 */
sealed class HistoryListItem {

    data class DateHeader(
        val date: String
    ) : HistoryListItem()

    data class HistoryEntry(
        val id: String,
        val type: HistoryType,
        val time: String,
        val date: String,
        val videoUri: String? = null
    ) : HistoryListItem()
}

enum class FilterType { ALL, FALL, WARNING }
