package com.example.on_safe.data.fake

import com.example.on_safe.data.repository.AccidentHistoryRepository
import com.example.on_safe.ui.history.HistoryListItem
import com.example.on_safe.ui.history.HistoryType

// TODO: GET /accident/history API 연동 후 교체
class FakeAccidentHistoryRepository : AccidentHistoryRepository {

    override fun getHistoryEntries(): MutableList<HistoryListItem.HistoryEntry> = mutableListOf(
        HistoryListItem.HistoryEntry("1", HistoryType.FALL,    "14:32", "2025.01.15"),
        HistoryListItem.HistoryEntry("2", HistoryType.WARNING, "09:17", "2025.01.15"),
        HistoryListItem.HistoryEntry("3", HistoryType.FALL,    "22:05", "2025.01.14"),
        HistoryListItem.HistoryEntry("4", HistoryType.WARNING, "11:44", "2025.01.13"),
        HistoryListItem.HistoryEntry("5", HistoryType.FALL,    "07:30", "2025.01.13"),
    )
}