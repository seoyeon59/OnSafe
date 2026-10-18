package com.example.on_safe.util

import android.util.Base64
import com.google.gson.JsonParser

/**
 * access 토큰(JWT)의 만료 시각 확인 — 서명은 검증하지 않고 payload의 exp만 읽는다(판단은 서버 몫).
 * WS는 REST와 달리 401 자동 refresh(인증기)를 거치지 않아, 연결 전에 만료를 미리 확인할 때 쓴다.
 */
object JwtExpiry {

    // 연결 핸드셰이크 도중 만료되지 않도록 이만큼 남았으면 이미 만료된 것으로 본다
    const val DEFAULT_SKEW_MS = 30_000L

    private val androidDecoder: (String) -> ByteArray = {
        Base64.decode(it, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    /** exp(밀리초). 형식이 아니거나 exp가 없으면 null — 판단할 수 없으니 호출부는 그대로 연결해 서버 응답(4001)에 맡긴다 */
    fun expiresAtMillis(token: String, decode: (String) -> ByteArray = androidDecoder): Long? {
        val payload = token.split('.').getOrNull(1) ?: return null
        return try {
            val json = JsonParser.parseString(String(decode(payload), Charsets.UTF_8)).asJsonObject
            json.get("exp")?.takeIf { it.isJsonPrimitive }?.asLong?.times(1000)
        } catch (_: Exception) {
            null
        }
    }

    fun isExpired(
        token: String,
        nowMillis: Long = System.currentTimeMillis(),
        skewMs: Long = DEFAULT_SKEW_MS,
        decode: (String) -> ByteArray = androidDecoder
    ): Boolean {
        val exp = expiresAtMillis(token, decode) ?: return false
        return exp - skewMs <= nowMillis
    }
}
