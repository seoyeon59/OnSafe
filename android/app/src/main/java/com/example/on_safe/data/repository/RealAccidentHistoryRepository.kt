package com.example.on_safe.data.repository

import com.example.on_safe.network.dto.FallLogResponse
import com.example.on_safe.ui.history.HistoryListItem
import com.example.on_safe.ui.history.HistoryType
import com.example.on_safe.util.DisplayText

class RealAccidentHistoryRepository : AccidentHistoryRepository {

    override suspend fun getHistoryEntries(userId: String): List<HistoryListItem.HistoryEntry> =
        FallLogSource.fetchLogs(userId, "사고 이력을 불러오지 못했습니다.")
            .map { it.toHistoryEntry() }

    // timestamp "yyyy-MM-dd'T'HH:mm:ss" 전제 — 인덱스 대신 구분자 파싱으로 형식 오류 시 크래시 방지
    private fun FallLogResponse.toHistoryEntry(): HistoryListItem.HistoryEntry {
        val timePart = timestamp.substringAfter('T', "").take(5)   // "HH:mm"
        val datePart = timestamp.substringBefore('T')               // "yyyy-MM-dd"
        return HistoryListItem.HistoryEntry(
            id = logId,
            type = if (FallLogSource.isFall(this)) HistoryType.FALL else HistoryType.WARNING,
            time = if (timePart.length == 5) timePart else DisplayText.NO_TIME,
            date = if (datePart.length == 10) datePart.replace("-", ".") else DisplayText.NO_DATE,
            hasVideo = hasVideo,
            videoStatus = videoStatus
        )
    }
}
