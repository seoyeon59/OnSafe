package com.example.on_safe.ui.live

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.LiveSessionResponse
import com.example.on_safe.network.failure
import com.example.on_safe.network.isOk
import io.livekit.android.LiveKit
import io.livekit.android.events.DisconnectReason
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import livekit.org.webrtc.RendererCommon
import java.text.SimpleDateFormat
import java.util.Locale

/** 실시간 영상 화면 상태 — 홈·전체화면 공용 */
sealed class LiveViewState {
    /** 시청 전 — 홈은 '실시간 보기' 버튼 표시 */
    object Idle : LiveViewState()

    /** 세션 요청·방 접속·피보호자 영상 대기 중 */
    object Connecting : LiveViewState()

    /** 피보호자 영상 트랙을 실제로 수신 중 — LIVE 배지는 이때만 */
    object Live : LiveViewState()

    /**
     * 시청 종료·실패 안내.
     * closeScreen: 더 볼 수 없는 종료(동의 철회·서버의 방 삭제) — 전체화면은 닫는다.
     */
    data class Stopped(val message: String?, val closeScreen: Boolean = false) : LiveViewState()
}

/**
 * 보호자 실시간 영상 시청(LiveKit, 구독 전용 토큰).
 *
 * 흐름: `POST /api/live/{elder}/session` → (request_delivered=false면 즉시 안내·DELETE)
 *   → LiveKit 방 접속 → 참가자 `elder-{id}`의 영상 트랙을 약 30초 대기(없으면 안내·DELETE)
 *   → 시청 중 약 4분마다 같은 POST로 자동 연장(응답 expires_at으로 다음 연장 시점 조정)
 *   → 화면 이탈·백그라운드 시 [stop]에서 DELETE. 서버가 방을 지우면(만료·해제·동의 철회) SDK 끊김 이벤트로 정리.
 *
 * 화면(Activity)의 onStart/onStop에 맞춰 [start]/[stop]을 부른다 — 포그라운드에서만 시청·연장한다.
 * 연장이 횟수 제한·열람 기록에서 빠지는 것은 서버가 "진행 중 세션이 있으면 연장"으로 판정해 처리한다.
 */
class LiveViewController(
    context: Context,
    private val scope: CoroutineScope,
    // 영상 렌더러를 담을 자리 — 세션마다 렌더러를 새로 만들어 넣는다(방마다 EGL 컨텍스트가 달라 재사용 불가)
    private val videoFrame: FrameLayout,
    private val scalingType: RendererCommon.ScalingType,
    private val onState: (LiveViewState) -> Unit
) {
    private val appContext = context.applicationContext

    private var sessionJob: Job? = null
    private var room: Room? = null
    private var renderer: TextureViewRenderer? = null
    private var attachedTrack: VideoTrack? = null

    // 서버 세션을 열어 둔 피보호자 — DELETE 대상. 닫았으면 null
    private var openElderUserId: String? = null

    // 우리가 끊는 중인지 — 직접 끊은 Disconnected 이벤트를 서버 종료로 오인하지 않게
    private var closingByClient = false

    private val videoReceiving = MutableStateFlow(false)

    var state: LiveViewState = LiveViewState.Idle
        private set

    // 코루틴의 isActive와 이름이 겹치면 runSession 루프 조건이 이쪽으로 해석될 수 있어 분리
    val isRunning: Boolean get() = sessionJob?.isActive == true

    fun start(elderUserId: String) {
        if (isRunning) return
        setState(LiveViewState.Connecting)
        sessionJob = scope.launch {
            try {
                runSession(elderUserId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                terminate(LiveViewState.Stopped("실시간 영상 연결에 실패했습니다. 잠시 후 다시 시도해 주세요."), endSession = true)
            }
        }
    }

    /**
     * 시청 중단. endSession=false는 홈↔전체화면 전환처럼 다른 화면이 이어서 보는 경우 —
     * 서버 세션을 남겨 두면 다음 화면의 POST가 연장으로 합류해 피보호자에게 요청이 다시 가지 않는다.
     */
    fun stop(endSession: Boolean) {
        sessionJob?.cancel()
        sessionJob = null
        releaseRoom()
        if (endSession) endServerSession()
        else openElderUserId = null   // 다음 화면이 넘겨받음 — 여기서는 더 이상 관리하지 않는다
        setState(LiveViewState.Idle)
    }

    private suspend fun runSession(elderUserId: String) = coroutineScope {
        val first = requestSession(elderUserId, isRenewal = false) ?: return@coroutineScope
        // 새 세션인데 피보호자 기기에 송출 요청이 안 갔다 — 기다려도 영상이 오지 않는다
        if (first.requestDelivered == false) {
            terminate(LiveViewState.Stopped("피보호자 기기에 요청을 보내지 못했습니다."), endSession = true)
            return@coroutineScope
        }
        var deadline = deadlineMillis(first.expiresAt)

        val newRoom = LiveKit.create(appContext).also { room = it }
        val newRenderer = TextureViewRenderer(videoFrame.context).also { r ->
            newRoom.initVideoRenderer(r)
            r.setScalingType(scalingType)
            r.setEnableHardwareScaler(true)
            videoFrame.addView(r, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            renderer = r
        }
        val elderIdentity = ELDER_IDENTITY_PREFIX + elderUserId

        // SharedFlow라 접속 전에 구독해야 접속 직후 이벤트(기존 송출 트랙 구독 등)를 놓치지 않는다
        launch(start = CoroutineStart.UNDISPATCHED) {
            newRoom.events.collect { event -> handleRoomEvent(event, elderIdentity, newRenderer) }
        }

        closingByClient = false
        newRoom.connect(first.serverUrl, first.token)

        // 피보호자가 송출을 시작할 때까지 대기 — 연장 합류(이미 송출 중)여도 같은 상한
        val gotVideo = withTimeoutOrNull(VIDEO_WAIT_MS) { videoReceiving.first { it } }
        if (gotVideo == null) {
            terminate(LiveViewState.Stopped("피보호자 카메라 영상을 받지 못했습니다. 카메라 앱이 켜져 있는지 확인해 주세요."), endSession = true)
            return@coroutineScope
        }

        // 자동 연장 — 화면이 떠 있는 동안만(onStop에서 이 Job이 취소된다)
        while (this.isActive) {
            delay(nextRenewDelay(deadline))
            val renewed = requestSession(elderUserId, isRenewal = true, deadline = deadline)
            if (renewed != null) deadline = deadlineMillis(renewed.expiresAt)
            else if (!isRunning) return@coroutineScope   // 연장 실패로 종료됨
        }
    }

    private fun handleRoomEvent(event: RoomEvent, elderIdentity: String, target: TextureViewRenderer) {
        when (event) {
            is RoomEvent.TrackSubscribed -> {
                val track = event.track as? VideoTrack ?: return
                if (event.participant.identity?.value != elderIdentity) return
                attachedTrack?.removeRenderer(target)
                track.addRenderer(target)
                attachedTrack = track
                videoReceiving.value = true
                setState(LiveViewState.Live)
            }
            is RoomEvent.TrackUnsubscribed -> {
                if (event.track != attachedTrack) return
                attachedTrack?.removeRenderer(target)
                attachedTrack = null
                videoReceiving.value = false
                // 송출이 잠시 끊김 — 방이 살아 있으면 재송출을 기다린다(LIVE 배지는 내림)
                setState(LiveViewState.Connecting)
            }
            is RoomEvent.Disconnected -> {
                if (closingByClient) return
                // 서버가 방을 지움(만료·연결 해제·동의 철회 등) 또는 네트워크 단절 — 화면 정리.
                // DELETE는 멱등이라 단절로 끊긴 경우에도 서버 세션을 확실히 닫도록 보낸다.
                val message = if (event.reason == DisconnectReason.ROOM_DELETED) {
                    "실시간 영상이 종료되었습니다."
                } else {
                    "실시간 영상 연결이 끊겼습니다."
                }
                terminate(LiveViewState.Stopped(message, closeScreen = true), endSession = event.reason != DisconnectReason.ROOM_DELETED)
            }
            else -> Unit
        }
    }

    /**
     * 시작·연장 요청. 실패하면 안내까지 처리하고 null.
     * 연장의 네트워크 오류는 만료 전까지 재시도 여지가 있어 세션을 유지한다.
     */
    private suspend fun requestSession(
        elderUserId: String,
        isRenewal: Boolean,
        deadline: Long = 0L
    ): LiveSessionResponse? {
        val response = try {
            ApiClient.api.startLiveSession(elderUserId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (isRenewal && System.currentTimeMillis() < deadline - RENEW_RETRY_MARGIN_MS) {
                return null   // 다음 루프에서 재시도(nextRenewDelay가 짧은 간격을 준다)
            }
            terminate(LiveViewState.Stopped("네트워크 오류로 실시간 영상을 이어가지 못했습니다."), endSession = isRenewal)
            return null
        }
        val data = response.body()?.data
        if (response.isOk && data != null) {
            openElderUserId = elderUserId
            return data
        }

        val failure = response.failure("실시간 영상을 시작하지 못했습니다.")
        val code = failure.code
        val status = response.code()
        // 연장 중 동의 철회 — 더 볼 수 없으니 화면을 닫는다(서버가 방도 지운다)
        if (isRenewal && code == CODE_LIVE_NOT_ALLOWED) {
            terminate(LiveViewState.Stopped("피보호자가 영상 동의를 철회해 실시간 영상을 종료합니다.", closeScreen = true), endSession = false)
            return null
        }
        val message = when {
            code == CODE_LIVE_NOT_ALLOWED -> "피보호자의 영상 동의가 필요합니다."
            code == CODE_LIVE_DEVICE_OFFLINE || status == 409 -> "카메라 앱이 켜져 있는지 확인해 주세요."
            code == CODE_LIVE_UNAVAILABLE || status == 503 -> "실시간 영상을 사용할 수 없습니다. 잠시 후 다시 시도해 주세요."
            code == CODE_TOO_MANY_REQUESTS || status == 429 -> "실시간 영상 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."
            else -> failure.message
        }
        // 시작 실패면 열린 세션이 없다. 연장 실패면 세션을 닫는다(연결 해제 403 등)
        terminate(LiveViewState.Stopped(message, closeScreen = isRenewal), endSession = isRenewal)
        return null
    }

    // 실패·서버 종료 처리 — 진행 중 Job·방을 정리하고 필요하면 서버 세션도 닫는다
    private fun terminate(state: LiveViewState, endSession: Boolean) {
        val job = sessionJob
        sessionJob = null
        releaseRoom()
        if (endSession) endServerSession() else openElderUserId = null
        setState(state)
        job?.cancel()
    }

    private fun releaseRoom() {
        closingByClient = true
        renderer?.let { r ->
            attachedTrack?.removeRenderer(r)
            videoFrame.removeView(r)
            r.release()
        }
        renderer = null
        attachedTrack = null
        videoReceiving.value = false
        room?.let {
            it.disconnect()
            it.release()
        }
        room = null
    }

    // 화면이 사라진 뒤에도 완료돼야 하므로 화면 수명과 무관한 범위에서 보낸다
    private fun endServerSession() {
        val elder = openElderUserId ?: return
        openElderUserId = null
        cleanupScope.launch {
            try {
                ApiClient.api.endLiveSession(elder)
            } catch (_: Exception) {
                // 실패해도 서버 세션은 최대 5분 뒤 만료·정리된다
            }
        }
    }

    private fun setState(newState: LiveViewState) {
        state = newState
        onState(newState)
    }

    // 다음 연장까지 대기 — 4분 주기, 단 응답의 만료 1분 전을 넘지 않게
    private fun nextRenewDelay(deadline: Long): Long {
        val untilMargin = deadline - System.currentTimeMillis() - RENEW_BEFORE_EXPIRY_MS
        return untilMargin.coerceAtMost(RENEW_INTERVAL_MS).coerceAtLeast(MIN_RENEW_DELAY_MS)
    }

    /**
     * expires_at → 기기 시각(ms). 서버 LocalDateTime(서버 시간대)이라 기기 시간대가 다르면 크게 어긋날 수 있어,
     * 세션 길이(5분) 범위를 벗어나면 "지금+5분"으로 본다.
     */
    private fun deadlineMillis(expiresAt: String?): Long {
        val now = System.currentTimeMillis()
        val fallback = now + SESSION_DURATION_MS
        val parsed = try {
            expiresAt?.let { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.KOREA).parse(it)?.time }
        } catch (_: Exception) {
            null
        } ?: return fallback
        return if (parsed in (now + 1)..(now + SESSION_DURATION_MS + CLOCK_SKEW_MS)) parsed else fallback
    }

    companion object {
        // 서버 LiveKit 참가자 식별자 규칙(LiveKitTokenIssuer) — 피보호자 송출자
        private const val ELDER_IDENTITY_PREFIX = "elder-"

        private const val CODE_LIVE_NOT_ALLOWED = "LIVE_NOT_ALLOWED"
        private const val CODE_LIVE_DEVICE_OFFLINE = "LIVE_DEVICE_OFFLINE"
        private const val CODE_LIVE_UNAVAILABLE = "LIVE_UNAVAILABLE"
        private const val CODE_TOO_MANY_REQUESTS = "TOO_MANY_REQUESTS"

        private const val VIDEO_WAIT_MS = 30_000L
        private const val SESSION_DURATION_MS = 5 * 60_000L
        private const val RENEW_INTERVAL_MS = 4 * 60_000L
        private const val RENEW_BEFORE_EXPIRY_MS = 60_000L
        private const val MIN_RENEW_DELAY_MS = 15_000L
        private const val RENEW_RETRY_MARGIN_MS = 10_000L
        private const val CLOCK_SKEW_MS = 60_000L

        private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
}
