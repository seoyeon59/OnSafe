package com.example.on_safe.messaging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * 서버 `live_request` data — 보호자가 새 실시간 영상 세션을 열었다는 신호.
 * 토큰은 싣지 않는다(기기 알림 로그 등에 남지 않게) — 송출 토큰은 `POST /api/live/me/publish-token`으로 따로 받는다.
 * [room]은 참고용(송출 토큰 응답에도 실린다).
 */
data class LiveRequest(val room: String?, val expiresAtMillis: Long) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean = nowMillis >= expiresAtMillis
}

/**
 * 실시간 영상 송출 요청(`live_request`) 수신함 — 피보호자(카메라 모드)용.
 *
 * - data 전용 메시지라 트레이 알림·알림함에 남기지 않는다(서버 알림 설정과도 무관).
 * - 이미 송출 중이면 무시한다 — 만료 직후 1분 안의 보호자 연장은 서버가 새 세션으로 처리해 요청이 다시 올 수 있다.
 * - 카메라 모드가 떠 있지 않을 때 온 요청은 만료 전까지 보관한다. 카메라 모드는 [pending]을 구독하다가
 *   [take]로 꺼내 송출을 시작한다(꺼낸 요청은 비워 같은 요청으로 두 번 시작하지 않게).
 */
object LiveRequestInbox {

    const val EVENT = "live_request"

    // expires_at 누락 시 서버 세션 길이(LiveService.SESSION_DURATION, 5분)로 본다
    private const val DEFAULT_TTL_MS = 5 * 60_000L

    private val _pending = MutableStateFlow<LiveRequest?>(null)
    val pending: StateFlow<LiveRequest?> = _pending.asStateFlow()

    // 송출 담당이 시작·종료 시 갱신 — 송출 중 들어온 요청을 버리는 기준
    @Volatile
    var isPublishing: Boolean = false

    /**
     * 메시징 서비스에서 호출. 보관했으면 true, 만료·송출 중이라 버렸으면 false.
     * @param data FCM data(`event`·`room`·`expires_at` epoch 초)
     */
    fun receive(data: Map<String, String>, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val request = parse(data, nowMillis)
        if (request.isExpired(nowMillis)) return false
        if (isPublishing) return false
        _pending.value = request
        return true
    }

    /** 보관된 요청을 꺼낸다 — 만료됐으면 null. 꺼내면 비운다. */
    fun take(nowMillis: Long = System.currentTimeMillis()): LiveRequest? =
        _pending.getAndUpdate { null }?.takeUnless { it.isExpired(nowMillis) }

    /** 로그아웃·카메라 모드 종료 등으로 더 송출할 수 없을 때 보관분 폐기 */
    fun clear() {
        _pending.value = null
    }

    internal fun parse(data: Map<String, String>, nowMillis: Long): LiveRequest {
        val expiresAtMillis = data["expires_at"]?.trim()?.toLongOrNull()?.let { it * 1000 }
            ?: (nowMillis + DEFAULT_TTL_MS)
        return LiveRequest(room = data["room"]?.takeIf { it.isNotBlank() }, expiresAtMillis = expiresAtMillis)
    }
}
