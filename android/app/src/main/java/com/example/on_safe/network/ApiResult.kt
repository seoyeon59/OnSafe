package com.example.on_safe.network

import com.example.on_safe.network.dto.ApiResponse
import retrofit2.Response

/**
 * ApiResponse 응답 판정 공통 처리.
 * 모든 뷰모델에 같은 성공 조건과 오류 문구 조립이 복사돼 있던 것을 통합.
 */

/** HTTP 성공 + 본문 success 동시 충족 */
val <T> Response<ApiResponse<T>>.isOk: Boolean
    get() = isSuccessful && body()?.success == true

/** 실패 문구 — 본문 message 우선, 없으면 errorBody 파싱, 그래도 없으면 fallback */
fun <T> Response<ApiResponse<T>>.errorMessage(fallback: String): String =
    body()?.message ?: ApiClient.parseErrorMessage(errorBody(), fallback)
