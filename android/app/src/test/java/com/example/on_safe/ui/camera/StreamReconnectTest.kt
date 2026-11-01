package com.example.on_safe.ui.camera

import com.example.on_safe.ui.camera.StreamClosePolicy.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StreamReconnectTest {

    @Test
    fun `종료 코드별 처리 - 4001 refresh, 4003 로그인, 1011과 그 밖은 백오프`() {
        assertEquals(Action.REFRESH_AND_RECONNECT, StreamClosePolicy.actionFor(4001))
        assertEquals(Action.LOGIN, StreamClosePolicy.actionFor(4003))
        assertEquals(Action.BACKOFF, StreamClosePolicy.actionFor(1011))
        assertEquals(Action.BACKOFF, StreamClosePolicy.actionFor(1000))  // 서버가 정상 종료로 닫아도 촬영 중이면 다시 연결
        assertEquals(Action.BACKOFF, StreamClosePolicy.actionFor(1006))
        assertEquals(Action.BACKOFF, StreamClosePolicy.actionFor(null))  // 코드 없이 끊김
    }

    @Test
    fun `예전 세션 무효 코드 1008은 더 이상 로그인으로 보내지 않는다`() {
        assertEquals(Action.BACKOFF, StreamClosePolicy.actionFor(1008))
    }

    @Test
    fun `백오프는 1초부터 두 배씩 늘고 30초에서 멈춘다 - 흔들기 없음`() {
        val backoff = ReconnectBackoff(jitterRatio = 0.0)
        val delays = List(8) { backoff.nextDelayMs() }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L, 30_000L), delays)
        assertEquals(8, backoff.attempts)
    }

    @Test
    fun `재연결에 성공하면 처음부터 다시 센다`() {
        val backoff = ReconnectBackoff(jitterRatio = 0.0)
        repeat(4) { backoff.nextDelayMs() }
        backoff.reset()
        assertEquals(0, backoff.attempts)
        assertEquals(1_000L, backoff.nextDelayMs())
    }

    @Test
    fun `흔들기는 ±20% 안이고 상한을 넘지 않는다`() {
        val backoff = ReconnectBackoff(random = Random(42))
        repeat(200) { i ->
            val nominal = minOf(1_000L shl minOf(i, 10), 30_000L)
            val delay = backoff.nextDelayMs()
            assertTrue("delay=$delay nominal=$nominal", delay >= nominal * 0.8 - 1 && delay <= minOf(nominal * 1.2 + 1, 30_000.0))
        }
    }
}
