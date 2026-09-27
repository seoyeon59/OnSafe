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

/**
 * 실패 문구 — 본문 message 우선, 없으면 errorBody 파싱, 그래도 없으면 fallback.
 * 서버가 200 + success:false 로 검증 오류를 줄 수도 있어 본문 경로도 같은 정제를 거친다.
 */
fun <T> Response<ApiResponse<T>>.errorMessage(fallback: String): String =
    body()?.message?.let { ApiClient.sanitizeMessage(it, fallback) }
        ?: ApiClient.parseErrorMessage(errorBody(), fallback)

/** 실패 코드(ErrorCode 이름) + 사용자 노출 문구 */
data class ApiFailure(val code: String?, val message: String)

/**
 * 실패 정보 일괄 추출 — errorBody는 1회만 읽을 수 있어 코드·문구를 한 번에 꺼냄.
 * 코드로 분기할 화면(REAUTH_REQUIRED 등)에서 errorMessage() 대신 사용.
 */
fun <T> Response<ApiResponse<T>>.failure(fallback: String): ApiFailure {
    val parsed = body() ?: ApiClient.parseErrorBody(errorBody())
    return ApiFailure(parsed?.code, ApiClient.sanitizeMessage(parsed?.message, fallback))
}
