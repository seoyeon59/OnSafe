package com.example.on_safe.data.repository

import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.FallLogResponse
import com.example.on_safe.ui.history.HistoryListItem
import com.example.on_safe.ui.history.HistoryType

class RealAccidentHistoryRepository : AccidentHistoryRepository {

    override suspend fun getHistoryEntries(userId: String): List<HistoryListItem.HistoryEntry> {
        val response = ApiClient.api.getFallLogs(userId)
        val body = response.body()
        if (response.isSuccessful && body?.success == true && body.data != null) {
            return body.data["logs"].orEmpty().map { it.toHistoryEntry() }
        }
        throw IllegalStateException(
            ApiClient.parseErrorMessage(response.errorBody(), "사고 이력을 불러오지 못했습니다.")
        )
    }

    // 백엔드 RiskLevel.DANGER_THRESHOLD(score>75 strict, 낙상 임계값 정책)와 동일 기준으로 위험/주의 분류
    private fun FallLogResponse.toHistoryEntry(): HistoryListItem.HistoryEntry {
        val type = if (fall || score > 75f) HistoryType.FALL else HistoryType.WARNING
        return HistoryListItem.HistoryEntry(
            id = logId,
            type = type,
            time = timestamp.substring(11, 16),
            date = timestamp.take(10).replace("-", "."),
            hasVideo = hasVideo
        )
    }
}
