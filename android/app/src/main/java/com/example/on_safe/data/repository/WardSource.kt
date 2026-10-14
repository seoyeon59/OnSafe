package com.example.on_safe.data.repository

import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.errorMessage
import com.example.on_safe.network.isOk

/**
 * 보호자에게 연결된 피보호자 조회 `GET /api/guardian/{userId}/wards`.
 * 사고 이력·영상은 카메라를 켠 피보호자 기준으로 저장돼 있어 조회 대상 ID가 필요하다.
 */
internal object WardSource {

    /** 1:1 정책상 최대 1명 — 연결 없으면 null. 조회 실패는 "미연결"과 구분되도록 예외로 던진다. */
    suspend fun fetchPairedWardUserId(guardianUserId: String): String? {
        val response = ApiClient.api.getWards(guardianUserId)
        if (!response.isOk) {
            throw IllegalStateException(response.errorMessage("연결된 피보호자를 확인하지 못했습니다."))
        }
        return response.body()?.data?.wards?.firstOrNull()?.userId
    }
}
