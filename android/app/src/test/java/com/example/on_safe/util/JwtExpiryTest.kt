package com.example.on_safe.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class JwtExpiryTest {

    // JVM 테스트에는 android.util.Base64가 없어 JDK 디코더를 넘긴다
    private val decode: (String) -> ByteArray = { Base64.getUrlDecoder().decode(it) }

    private fun token(payloadJson: String): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        return "${enc.encodeToString("""{"alg":"HS256"}""".toByteArray())}." +
            "${enc.encodeToString(payloadJson.toByteArray())}.signature"
    }

    @Test
    fun `exp를 밀리초로 읽는다`() {
        assertEquals(1_700_000_000_000L, JwtExpiry.expiresAtMillis(token("""{"sub":"admin","exp":1700000000}"""), decode))
    }

    @Test
    fun `만료 30초 전부터 만료로 본다`() {
        val t = token("""{"exp":1000}""")  // 1_000_000ms
        assertFalse(JwtExpiry.isExpired(t, nowMillis = 969_999, decode = decode))
        assertTrue(JwtExpiry.isExpired(t, nowMillis = 970_000, decode = decode))
        assertTrue(JwtExpiry.isExpired(t, nowMillis = 2_000_000, decode = decode))
    }

    @Test
    fun `형식이 아니거나 exp가 없으면 판단하지 않는다 - 만료 아님으로 보고 서버에 맡김`() {
        assertNull(JwtExpiry.expiresAtMillis("not-a-jwt", decode))
        assertNull(JwtExpiry.expiresAtMillis(token("""{"sub":"admin"}"""), decode))
        assertNull(JwtExpiry.expiresAtMillis("a.!!!.c", decode))
        assertFalse(JwtExpiry.isExpired("not-a-jwt", nowMillis = Long.MAX_VALUE, decode = decode))
    }
}
