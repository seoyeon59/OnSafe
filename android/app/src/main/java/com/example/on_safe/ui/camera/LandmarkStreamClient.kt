package com.example.on_safe.ui.camera

import android.util.Log
import com.example.on_safe.BuildConfig
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.FrameMessage
import com.example.on_safe.network.dto.InitMessage
import com.example.on_safe.network.dto.LandmarkPoint
import com.example.on_safe.network.dto.StreamServerMessage
import com.example.on_safe.util.JwtExpiry
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Python AI 서버 WS /ws/stream 연결 — landmark 전송 + 추론 결과 수신.
 * 엔드포인트는 BuildConfig.AI_WS_URL (BASE_URL과 동일 컨벤션).
 *
 * 촬영 중 연결이 끊기면 스스로 다시 연결한다(서버는 최대 1시간마다 연결을 끊는다).
 * - 연결(재연결 포함) 전에 access 만료를 확인해 만료면 refresh부터 한다 — REST 401 처리와 같은 [ApiClient.refreshTokens]
 * - 재연결할 때마다 init부터 다시 보내고, init_ok 전까지는 프레임을 보내지 않는다.
 *   프레임 timestamp는 연결마다 0부터 센다([Listener.onInitOk] reconnected=true) — 서버가 init마다 초기화한다
 * - 종료 코드별 처리는 [StreamClosePolicy]: 4001 refresh 후 재연결 / 4003 로그인(재연결 안 함) / 그 밖은 [ReconnectBackoff]
 * - 첫 연결이 성립하기 전(init_ok 전) 실패는 자동 재시도하지 않고 [Listener.onConnectFailed] — 사용자가 다시 시작한다.
 *   단 4001은 refresh 후 한 번 더 시도한다(앱을 오래 켜 둔 뒤 촬영을 시작하면 access가 만료돼 있다)
 *
 * 연결 상태는 [scope](메인 스레드)에서만 바꾼다. OkHttp 콜백은 [scope]로 넘겨 처리한다.
 */
class LandmarkStreamClient(
    private val listener: Listener,
    private val scope: CoroutineScope,
    private val accessToken: () -> String?,
    // 만료된 access로 refresh — REST 401 인증기와 같은 경로(동시 교체 직렬화)
    private val refreshTokens: suspend (staleAccessToken: String?) -> ApiClient.RefreshOutcome = { stale ->
        withContext(Dispatchers.IO) { ApiClient.refreshTokens(stale) }
    },
    private val isTokenExpired: (String) -> Boolean = { JwtExpiry.isExpired(it) }
) {

    /** [onResult]만 OkHttp 스레드, 나머지는 메인 스레드에서 호출된다. */
    interface Listener {
        /** init_ok — [reconnected]가 false면 첫 연결, true면 끊긴 뒤 다시 연결됨 */
        fun onInitOk(reconnected: Boolean)
        fun onResult(fallScore: Float, fall: Boolean, level: String, logId: String?)
        /** 첫 연결이 성립하지 못함 — 자동 재시도하지 않는다. [serverError]는 1011(서버 일시 장애) */
        fun onConnectFailed(serverError: Boolean)
        /** 촬영 중 연결이 끊겨 [delayMs] 뒤 [attempt]번째 재연결을 시도한다 */
        fun onReconnecting(attempt: Int, delayMs: Long)
        /** 4003 또는 refresh 거부 — 로그인이 필요하다. 재연결하지 않는다 */
        fun onSessionInvalid()
    }

    companion object {
        private val BASE_WS_URL = BuildConfig.AI_WS_URL
        private const val TAG = "LandmarkStreamClient"

        // 촬영마다 새 인스턴스가 생기므로 OkHttpClient(스레드풀·커넥션풀)는 공유 재사용
        private val sharedClient = OkHttpClient.Builder().build()

        // init_ok 없이 4001이 연달아 오면(시계 차이 등) refresh를 반복하지 않는다
        private const val MAX_REFRESH_PER_ATTEMPT = 1
    }

    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    private var userId = ""
    private var deviceId = ""

    // 현재 연결 시도 중인 소켓 — 교체된 이전 소켓의 늦은 콜백을 걸러 내는 기준
    private var socket: WebSocket? = null

    // init_ok를 받은 소켓 — 프레임은 여기로만 보낸다(분석 스레드에서 읽음)
    @Volatile
    private var readySocket: WebSocket? = null

    private var everConnected = false
    private var closedByApp = false
    private var refreshCount = 0
    private var reconnectJob: Job? = null
    private val backoff = ReconnectBackoff()

    fun connect(userId: String, deviceId: String) {
        this.userId = userId
        this.deviceId = deviceId
        reconnectJob = scope.launch { openWithFreshToken() }
    }

    // 연결 전에 access 만료를 먼저 확인한다 — 만료됐거나 곧 만료면 refresh부터(4001 왕복을 줄인다).
    // refresh가 통신 실패면 그대로 연결해 서버 응답(4001 → refresh 재시도, 그 밖은 백오프)에 맡긴다.
    private suspend fun openWithFreshToken() {
        if (closedByApp) return
        val token = accessToken()
        if (!token.isNullOrBlank() && isTokenExpired(token)) {
            Log.d(TAG, "access 만료 — 연결 전에 refresh")
            if (refreshTokens(token) == ApiClient.RefreshOutcome.INVALID) {
                listener.onSessionInvalid()
                return
            }
        }
        open()
    }

    // TODO: [보안] 토큰이 쿼리스트링에 실려 프록시·서버 접근 로그에 남을 수 있음.
    //       서버가 Sec-WebSocket-Protocol 또는 Authorization 헤더 인증을 지원하면 전환 필요.
    private fun open() {
        if (closedByApp) return
        val token = accessToken()
        if (token.isNullOrBlank()) {
            listener.onSessionInvalid()
            return
        }
        val request = Request.Builder()
            .url("$BASE_WS_URL?token=$token")
            .build()
        socket = sharedClient.newWebSocket(request, SocketListener())
    }

    private inner class SocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val init = InitMessage(userId = userId, deviceId = deviceId)
            webSocket.send(gson.toJson(init))
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = try {
                gson.fromJson(text, StreamServerMessage::class.java)
            } catch (e: Exception) {
                // 원문에는 서버 응답이 그대로 담겨 릴리즈 로그에는 남기지 않는다
                if (BuildConfig.DEBUG) Log.w(TAG, "서버 메시지 파싱 실패: $text", e)
                else Log.w(TAG, "서버 메시지 파싱 실패")
                return
            }
            when (message.type) {
                "init_ok" -> scope.launch { onInitOk(webSocket) }
                "result" -> if (readySocket === webSocket) listener.onResult(
                    message.fallScore ?: 0f,
                    message.fall ?: false,
                    message.level ?: "정상",
                    message.logId
                )
            }
        }

        // 종료 코드 없이 끊김 — 네트워크 끊김·서버 재시작·Cloud Run 1시간 제한 등
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "WS 끊김(코드 없음)", t)
            scope.launch { onEnded(webSocket, code = null) }
        }

        // 서버가 먼저 끊는 경우 종료 코드는 onClosing에서만 확실히 전달됨. 응답 close로 핸드셰이크 마무리
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            scope.launch { onEnded(webSocket, code) }
        }
    }

    private fun onInitOk(webSocket: WebSocket) {
        if (closedByApp || webSocket !== socket) return
        val reconnected = everConnected
        everConnected = true
        backoff.reset()
        refreshCount = 0
        // 프레임 전송을 열기 전에 알린다 — 재연결이면 리스너가 프레임 시계를 0부터 다시 세게 한다
        listener.onInitOk(reconnected)
        readySocket = webSocket
    }

    private fun onEnded(webSocket: WebSocket, code: Int?) {
        // 앱이 닫았거나 이미 다른 소켓으로 바뀐 뒤의 콜백 — 무시
        if (closedByApp || webSocket !== socket) return
        socket = null
        readySocket = null
        Log.d(TAG, "WS 종료 code=$code (연결된 적 있음=$everConnected)")

        when (StreamClosePolicy.actionFor(code)) {
            StreamClosePolicy.Action.LOGIN -> listener.onSessionInvalid()
            StreamClosePolicy.Action.REFRESH_AND_RECONNECT ->
                if (refreshCount < MAX_REFRESH_PER_ATTEMPT) refreshAndReconnect(staleToken = accessToken())
                else retryOrFail(serverError = false)
            StreamClosePolicy.Action.BACKOFF -> retryOrFail(serverError = code == StreamClosePolicy.SERVER_ERROR)
        }
    }

    private fun refreshAndReconnect(staleToken: String?) {
        refreshCount++
        reconnectJob = scope.launch {
            when (refreshTokens(staleToken)) {
                ApiClient.RefreshOutcome.OK -> open()
                ApiClient.RefreshOutcome.INVALID -> listener.onSessionInvalid()
                // 통신 실패 — 세션은 살아 있으니 일반 끊김처럼 다룬다
                ApiClient.RefreshOutcome.FAILED -> retryOrFail(serverError = false)
            }
        }
    }

    // 촬영 중이었으면 백오프 재연결, 첫 연결 전이면 실패로 알린다
    private fun retryOrFail(serverError: Boolean) {
        if (closedByApp) return
        if (!everConnected) {
            listener.onConnectFailed(serverError)
            return
        }
        val delayMs = backoff.nextDelayMs()
        listener.onReconnecting(backoff.attempts, delayMs)
        reconnectJob = scope.launch {
            delay(delayMs)
            refreshCount = 0  // 백오프를 한 번 거쳤으면 다음 4001에서 다시 refresh해 본다
            openWithFreshToken()
        }
    }

    fun sendFrame(frameIndex: Int, timestampSec: Float, landmarks: List<LandmarkPoint>) {
        // 재연결 중(init_ok 전)에는 버린다 — init보다 프레임이 먼저 가면 서버가 거절한다
        val ws = readySocket ?: return
        val frame = FrameMessage(frame = frameIndex, timestamp = timestampSec, landmarks = landmarks)
        ws.send(gson.toJson(frame))
    }

    fun close() {
        closedByApp = true
        reconnectJob?.cancel()
        reconnectJob = null
        readySocket = null
        socket?.close(1000, null)
        socket = null
    }
}
