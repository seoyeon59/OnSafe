package com.example.on_safe.data.repository

import com.example.on_safe.ui.history.HistoryListItem

interface AccidentHistoryRepository {
    fun getHistoryEntries(): MutableList<HistoryListItem.HistoryEntry>
}