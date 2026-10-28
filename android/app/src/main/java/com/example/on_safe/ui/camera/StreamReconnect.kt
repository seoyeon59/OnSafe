package com.example.on_safe.ui.camera

import kotlin.random.Random

/**
 * WS /ws/stream 종료 처리 기준(API 명세 §3 "연결 종료 코드").
 * 서버는 연결을 먼저 수락한 뒤 인증하고 실패하면 아래 코드로 닫는다. 앱이 스스로 닫은 경우는 호출 전에 걸러진다.
 */
internal object StreamClosePolicy {

    const val TOKEN_EXPIRED = 4001  // access 만료(연결 시점) → refresh 후 재연결
    const val UNAUTHORIZED = 4003   // 토큰 없음·위조·로그아웃·세션 무효화 → 로그인, 재연결하지 않음
    const val SERVER_ERROR = 1011   // 토큰 상태 확인 불가(Redis 장애) → 백오프 재연결

    enum class Action { REFRESH_AND_RECONNECT, LOGIN, BACKOFF }

    /** @param code 서버 종료 코드. null이면 종료 코드 없이 끊김(네트워크 끊김·서버 재시작·1시간 제한 등) */
    fun actionFor(code: Int?): Action = when (code) {
        TOKEN_EXPIRED -> Action.REFRESH_AND_RECONNECT
        UNAUTHORIZED -> Action.LOGIN
        else -> Action.BACKOFF   // 1011·그 밖의 코드·코드 없음
    }
}

/**
 * 지수 백오프 — 1초에서 시작해 두 배씩, 최대 30초. 서버 재시작 직후 여러 기기가 같은 순간에 몰리지 않도록 ±20% 흔든다.
 * 재연결에 성공(init_ok)하면 [reset]으로 처음부터 다시 센다.
 */
internal class ReconnectBackoff(
    private val baseMs: Long = 1_000L,
    private val maxMs: Long = 30_000L,
    private val jitterRatio: Double = 0.2,
    private val random: Random = Random.Default
) {
    private var attempt = 0

    /** 지금까지 잡은 재연결 횟수(마지막 [reset] 이후) */
    val attempts: Int get() = attempt

    fun nextDelayMs(): Long {
        val exp = baseMs shl attempt.coerceAtMost(MAX_SHIFT)
        attempt++
        val capped = exp.coerceAtMost(maxMs)
        val jitter = (capped * jitterRatio * (random.nextDouble() * 2 - 1)).toLong()
        return (capped + jitter).coerceIn(baseMs / 2, maxMs)
    }

    fun reset() {
        attempt = 0
    }

    private companion object {
        const val MAX_SHIFT = 10  // 1초 × 2^10이면 이미 상한을 넘는다 — 시프트 넘침 방지
    }
}
