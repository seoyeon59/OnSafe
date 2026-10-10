package com.example.on_safe.messaging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * 서버 `live_request` data — 보호자가 새 실시간 영상 세션을 열었다는 신호.
 * 토큰은 싣지 않는다(기기 알림 로그 등에 남지 않게) — 송출 토큰은 `POST /api/live/me/publish-token`으로 따로 받는다.
 *
 * @property room 송출할 LiveKit 방(`live-{내 userId}`) — 송출 토큰 응답의 room과 대조용
 * @property expiresAtMillis 요청 만료 시각(**기기 시계 기준**) — 기기 시계 오차를 보정한 값
 */
data class LiveRequest(val room: String, val expiresAtMillis: Long) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean = nowMillis >= expiresAtMillis
}

/**
 * 실시간 영상 송출 요청(`live_request`) 수신함 — 피보호자(카메라 모드)용.
 *
 * - data 전용 메시지라 트레이 알림·알림함에 남기지 않는다(서버 알림 설정과도 무관).
 * - 내 방(`live-{내 userId}`)이 아닌 요청은 버린다 — 기기에서 계정을 바꿨는데 이전 계정의 FCM 토큰이
 *   서버에 남아 있으면 남의 요청이 올 수 있다.
 * - 이미 송출 중이면 무시한다 — 만료 직후 1분 안의 보호자 연장은 서버가 새 세션으로 처리해 요청이 다시 올 수 있다.
 * - 카메라 모드가 떠 있지 않을 때 온 요청은 만료 전까지 보관한다. 카메라 모드는 [pending]을 구독하다가
 *   [take]로 꺼내 송출을 시작한다(꺼낸 요청은 비워 같은 요청으로 두 번 시작하지 않게).
 */
object LiveRequestInbox {

    const val EVENT = "live_request"

    // 서버 LiveKitTokenIssuer.roomName 규칙
    private const val ROOM_PREFIX = "live-"

    // 서버 세션 길이(LiveService.SESSION_DURATION) — expires_at 누락 시 기본값이자 남은 시간 상한
    private const val SESSION_TTL_MS = 5 * 60_000L

    // 서버 시각 반올림(epoch 초)·전송 지연 여유
    private const val TTL_SLACK_MS = 30_000L

    private val _pending = MutableStateFlow<LiveRequest?>(null)
    val pending: StateFlow<LiveRequest?> = _pending.asStateFlow()

    // 송출 담당이 시작·종료 시 갱신 — 송출 중 들어온 요청을 버리는 기준
    @Volatile
    var isPublishing: Boolean = false

    /**
     * 수신 결과 — 버린 사유는 디버그 로그용. 판정 순서: 로그아웃 → 다른 계정 → 만료 → 송출 중.
     * - [ACCEPTED] 받음: 보관(기존 보관분은 최신 요청으로 교체)
     * - [NOT_LOGGED_IN] 로그아웃 상태: 송출 토큰을 받을 수 없다
     * - [OTHER_ACCOUNT] 다른 계정 요청: room이 `live-{내 userId}`가 아님(이전 계정 FCM 토큰 잔존 등)
     * - [EXPIRED] 만료: 기기 기준 만료 시각이 지남
     * - [ALREADY_PUBLISHING] 이미 송출 중: 만료 직후 연장으로 재수신된 요청
     */
    enum class Result { ACCEPTED, NOT_LOGGED_IN, OTHER_ACCOUNT, EXPIRED, ALREADY_PUBLISHING }

    /**
     * 메시징 서비스에서 호출.
     * @param data FCM data(`event`·`room`·`expires_at` epoch 초)
     * @param myUserId 로그인한 본인 userId — 비어 있으면(로그아웃) 송출할 수 없어 버린다
     * @param sentTimeMillis FCM `RemoteMessage.sentTime`(서버 측 전송 시각, 0이면 미상)
     */
    fun receive(
        data: Map<String, String>,
        myUserId: String,
        sentTimeMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Result {
        if (myUserId.isBlank()) return Result.NOT_LOGGED_IN
        val request = parse(data, myUserId, sentTimeMillis, nowMillis) ?: return Result.OTHER_ACCOUNT
        if (request.isExpired(nowMillis)) return Result.EXPIRED
        if (isPublishing) return Result.ALREADY_PUBLISHING
        _pending.value = request
        return Result.ACCEPTED
    }

    /** 보관된 요청을 꺼낸다 — 만료됐으면 null. 꺼내면 비운다. */
    fun take(nowMillis: Long = System.currentTimeMillis()): LiveRequest? =
        _pending.getAndUpdate { null }?.takeUnless { it.isExpired(nowMillis) }

    /** 로그아웃·카메라 모드 종료 등으로 더 송출할 수 없을 때 보관분 폐기 */
    fun clear() {
        _pending.value = null
    }

    /** 로그아웃 상태이거나 다른 계정 요청이면 null */
    internal fun parse(
        data: Map<String, String>,
        myUserId: String,
        sentTimeMillis: Long,
        nowMillis: Long
    ): LiveRequest? {
        if (myUserId.isBlank()) return null
        val myRoom = ROOM_PREFIX + myUserId
        // room 누락은 서버 구버전 등으로 보고 내 방으로 간주, 다른 방이면 남의 요청
        val room = data["room"]?.trim()?.takeIf { it.isNotEmpty() } ?: myRoom
        if (room != myRoom) return null
        return LiveRequest(room = room, expiresAtMillis = localDeadline(data["expires_at"], sentTimeMillis, nowMillis))
    }

    /**
     * expires_at(서버 epoch 초) → 기기 시계 기준 만료 시각.
     * 기기 시계가 몇 분만 틀려도 절대 시각 비교로는 멀쩡한 요청을 만료로 버린다. sentTime(서버 측 시각)과의
     * 차이로 남은 시간을 구해 수신 시각에 더한다. sentTime을 모르면 절대 시각을 그대로 쓴다.
     */
    private fun localDeadline(expiresAtRaw: String?, sentTimeMillis: Long, nowMillis: Long): Long {
        val expiresAtMillis = expiresAtRaw?.trim()?.toLongOrNull()?.let { it * 1000 }
            ?: return nowMillis + SESSION_TTL_MS
        if (sentTimeMillis <= 0L) return expiresAtMillis
        val remaining = (expiresAtMillis - sentTimeMillis).coerceIn(0L, SESSION_TTL_MS + TTL_SLACK_MS)
        return nowMillis + remaining
    }
}
