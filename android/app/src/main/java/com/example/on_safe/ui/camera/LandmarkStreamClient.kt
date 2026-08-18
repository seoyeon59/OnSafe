package com.example.on_safe.ui.camera

import android.util.Log
import com.example.on_safe.BuildConfig
import com.example.on_safe.network.dto.FrameMessage
import com.example.on_safe.network.dto.InitMessage
import com.example.on_safe.network.dto.LandmarkPoint
import com.example.on_safe.network.dto.StreamServerMessage
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Python AI 서버 WS /ws/stream 연결 — landmark 전송 + 추론 결과 수신.
 * 엔드포인트는 BuildConfig.AI_WS_URL (build.gradle.kts, ApiClient.BASE_URL과 동일 컨벤션)에서 가져온다.
 */
class LandmarkStreamClient(private val listener: Listener) {

    interface Listener {
        fun onInitOk()
        fun onResult(fallScore: Float, fall: Boolean, level: String, logId: String?)
        fun onFailure(t: Throwable)
        fun onClosed()
    }

    companion object {
        private val BASE_WS_URL = BuildConfig.AI_WS_URL
        private const val TAG = "LandmarkStreamClient"

        // 촬영을 시작/종료할 때마다 새 LandmarkStreamClient 인스턴스가 만들어지는데,
        // 그때마다 OkHttpClient(내부 스레드풀·커넥션풀 포함)를 새로 만들면 자원이 낭비된다.
        // ApiClient와 같은 방식으로 인스턴스 간에 하나만 공유해서 재사용한다.
        private val sharedClient = OkHttpClient.Builder().build()
    }

    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    private var webSocket: WebSocket? = null

    fun connect(userId: String, deviceId: String, accessToken: String) {
        val request = Request.Builder()
            .url("$BASE_WS_URL?token=$accessToken")
            .build()

        webSocket = sharedClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val init = InitMessage(userId = userId, deviceId = deviceId)
                webSocket.send(gson.toJson(init))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = try {
                    gson.fromJson(text, StreamServerMessage::class.java)
                } catch (e: Exception) {
                    Log.w(TAG, "서버 메시지 파싱 실패: $text", e)
                    return
                }
                when (message.type) {
                    "init_ok" -> listener.onInitOk()
                    "result" -> listener.onResult(
                        message.fallScore ?: 0f,
                        message.fall ?: false,
                        message.level ?: "정상",
                        message.logId
                    )
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onFailure(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener.onClosed()
            }
        })
    }

    fun sendFrame(frameIndex: Int, timestampSec: Float, landmarks: List<LandmarkPoint>) {
        val frame = FrameMessage(frame = frameIndex, timestamp = timestampSec, landmarks = landmarks)
        webSocket?.send(gson.toJson(frame))
    }

    fun close() {
        webSocket?.close(1000, null)
        webSocket = null
    }
}
