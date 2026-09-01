package com.example.on_safe.data.repository

import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.FallLogResponse

/**
 * 사고이력·알림 공용 `GET /api/fall-logs/{userId}`.
 * 조회 절차·위험 분류 기준의 화면별 이원화 방지.
 */
internal object FallLogSource {

    // 백엔드 RiskLevel.DANGER_THRESHOLD(score > 75 strict)와 동일 기준
    private const val DANGER_THRESHOLD = 75f

    suspend fun fetchLogs(userId: String, errorFallback: String): List<FallLogResponse> {
        val response = ApiClient.api.getFallLogs(userId)
        val body = response.body()
        if (response.isSuccessful && body?.success == true && body.data != null) {
            return body.data["logs"].orEmpty()
        }
        throw IllegalStateException(
            ApiClient.parseErrorMessage(response.errorBody(), errorFallback)
        )
    }

    // 낙상(위험)/주의 판정 단일 기준
    fun isFall(log: FallLogResponse): Boolean = log.fall || log.score > DANGER_THRESHOLD
}
