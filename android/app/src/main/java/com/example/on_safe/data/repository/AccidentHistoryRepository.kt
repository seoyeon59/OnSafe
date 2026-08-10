package com.example.on_safe.data.repository

import com.example.on_safe.ui.history.HistoryListItem

interface AccidentHistoryRepository {
    suspend fun getHistoryEntries(userId: String): List<HistoryListItem.HistoryEntry>
}