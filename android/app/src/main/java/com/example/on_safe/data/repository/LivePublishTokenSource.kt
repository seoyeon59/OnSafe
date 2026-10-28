package com.example.on_safe.data.repository

import com.example.on_safe.messaging.LiveRequest
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.failure
import com.example.on_safe.network.isOk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** LiveKit 송출 접속 정보 — 송출 전용 토큰. [expiresAtMillis]는 기기 시계 기준 세션 만료 예정 시각 */
data class LivePublishCredentials(
    val serverUrl: String,
    val room: String,
    val token: String,
    val expiresAtMillis: Long
)

/** 송출 토큰 요청 결과 — 실패 사유별로 다음 행동이 달라 구분한다 */
sealed class PublishTokenResult {
    data class Success(val credentials: LivePublishCredentials) : PublishTokenResult()

    /** 404 LIVE_SESSION_NOT_FOUND — 보호자가 이미 종료했거나 세션 만료. 송출하지 않는다 */
    object SessionEnded : PublishTokenResult()

    /** 403 LIVE_NOT_ALLOWED — 요청 뒤 영상 동의를 철회. 송출하지 않는다 */
    object NotAllowed : PublishTokenResult()

    /** 응답 방이 요청 방과 다름 — 다른 계정·세션의 토큰일 수 있어 쓰지 않는다 */
    object RoomMismatch : PublishTokenResult()

    /** 재시도 후에도 실패(네트워크·503 등) */
    data class Failed(val reason: String) : PublishTokenResult()
}

/**
 * `POST /api/live/me/publish-token` — live_request를 받은 피보호자 기기가 송출 토큰을 받는다.
 * 일시적 실패(네트워크·5xx)는 요청 만료 전까지 짧은 간격으로 재시도한다 — 보호자는 약 30초만 기다린다.
 */
internal object LivePublishTokenSource {

    private const val CODE_LIVE_SESSION_NOT_FOUND = "LIVE_SESSION_NOT_FOUND"
    private const val CODE_LIVE_NOT_ALLOWED = "LIVE_NOT_ALLOWED"

    // 보호자 대기(약 30초) 안에 끝나도록 3회·2초→4초
    private const val MAX_ATTEMPTS = 3
    private const val RETRY_BASE_MS = 2_000L

    // 서버 세션 길이 — expires_at을 해석하지 못할 때의 상한
    private const val SESSION_TTL_MS = 5 * 60_000L

    suspend fun fetch(request: LiveRequest): PublishTokenResult {
        var lastReason = "unknown"
        repeat(MAX_ATTEMPTS) { attempt ->
            if (request.isExpired()) return PublishTokenResult.SessionEnded
            if (attempt > 0) delay(RETRY_BASE_MS shl (attempt - 1))

            val response = try {
                ApiClient.api.issueLivePublishToken()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastReason = "network: ${e.javaClass.simpleName}"
                return@repeat   // 재시도
            }

            val data = response.body()?.data
            if (response.isOk && data != null) {
                if (data.room != request.room) return PublishTokenResult.RoomMismatch
                return PublishTokenResult.Success(
                    LivePublishCredentials(
                        serverUrl = data.serverUrl,
                        room = data.room,
                        token = data.token,
                        // 송출 토큰 유효시간 = 남은 세션 시간. 요청에서 기기 시계로 보정한 만료가 더 정확하다
                        expiresAtMillis = request.expiresAtMillis
                            .coerceAtMost(System.currentTimeMillis() + SESSION_TTL_MS)
                    )
                )
            }

            val failure = response.failure("송출 토큰을 받지 못했습니다.")
            when {
                failure.code == CODE_LIVE_SESSION_NOT_FOUND || response.code() == 404 -> return PublishTokenResult.SessionEnded
                failure.code == CODE_LIVE_NOT_ALLOWED -> return PublishTokenResult.NotAllowed
                // 5xx(LIVE_UNAVAILABLE 등)는 일시적일 수 있어 재시도, 그 외 4xx는 재시도해도 같다
                response.code() >= 500 -> lastReason = "http ${response.code()} ${failure.code.orEmpty()}"
                else -> return PublishTokenResult.Failed("http ${response.code()} ${failure.code.orEmpty()}")
            }
        }
        return PublishTokenResult.Failed(lastReason)
    }
}
