package com.example.on_safe.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.on_safe.BuildConfig
import com.example.on_safe.data.repository.LivePublishCredentials
import com.example.on_safe.data.repository.LivePublishTokenSource
import com.example.on_safe.data.repository.PublishTokenResult
import com.example.on_safe.messaging.LiveRequest
import com.example.on_safe.messaging.LiveRequestInbox
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.DeviceRegisterRequest
import com.example.on_safe.network.dto.HeartbeatRequest
import io.livekit.android.room.Room
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// 조회 전/실패 구분용 null 유지 — 문구 결정은 DisplayText 담당
data class CameraModeUiState(
    val deviceId: String? = null,
    // 페어링 코드 오버레이 — null 이면 로딩 중 or 이미 페어링됨, 값이 있으면 화면 상단에 표시.
    // 서버 TTL(15분)에 맞춰 자동 재발급되므로 만료 케이스 UI 처리 불필요.
    val pairingCode: String? = null,
    // 이미 보호자와 연결됐는지 — 카메라 모드 진입 시 my-guardian 조회로 판정.
    // true 면 코드 자동 갱신을 스킵하고 오버레이 대신 "○○님과 연결됨" 표시.
    val isPaired: Boolean = false,
    // 연결된 보호자 정보 (isPaired=true 일 때만 유효). userId 는 unpair API 호출에 필요.
    val pairedGuardianUserId: String? = null,
    val pairedGuardianName: String? = null,
)

/** 실시간 영상 송출 준비 상태 — LiveKit 송출 담당이 [Ready]를 받아 송출을 시작한다 */
sealed class LivePublishState {
    object Idle : LivePublishState()

    /** live_request를 꺼내 송출 토큰을 받는 중 */
    object FetchingToken : LivePublishState()

    /** 송출 토큰 확보 — 곧바로 LiveKit 방 접속을 시작한다 */
    data class Ready(val credentials: LivePublishCredentials) : LivePublishState()

    /** 송출 전용 토큰으로 LiveKit 방 접속 완료 — 영상 트랙 송출 준비 중 */
    data class Connected(val room: String, val expiresAtMillis: Long) : LivePublishState()

    /**
     * 분석 프레임을 영상 트랙으로 송출 중 — 보호자가 보고 있는 상태.
     * [expiresAtMillis]는 **참고용**(발급 시점 기준 세션 만료 예정, 기기 시계). 보호자 자동 연장은
     * 피보호자에게 알리지 않아 실제 종료는 더 늦을 수 있다 — 이 값으로 송출을 멈추지 않는다.
     */
    data class Publishing(val room: String, val expiresAtMillis: Long) : LivePublishState()
}

class CameraModeViewModel : ViewModel() {

    private val _uiState = MutableLiveData(CameraModeUiState())
    val uiState: LiveData<CameraModeUiState> = _uiState

    private val _livePublishState = MutableStateFlow<LivePublishState>(LivePublishState.Idle)
    val livePublishState: StateFlow<LivePublishState> = _livePublishState.asStateFlow()

    // live_request 수신함 감시 — 카메라 모드 켜져 있는 동안만
    private var liveRequestJob: Job? = null

    // Ready를 아무도 넘겨받지 않으면 세션 만료 시각에 비운다 — 송출 중 표시가 남아 다음 요청을 막지 않게
    private var liveReadyExpiryJob: Job? = null

    // LiveKit 방 접속 — 화면 회전에도 끊기지 않게 ViewModel이 소유(Application Context 사용)
    private var liveRoomPublisher: LiveRoomPublisher? = null

    // 송출 중인 LiveKit 방 — 접속 전·종료 후 null
    var liveRoom: Room? = null
        private set

    // 분석 프레임 → LiveKit 영상 트랙
    private val liveVideoSource = LiveVideoSource()

    // 송출 시작 허용 조건 ① 카메라 모드 촬영 중 — 분석 프레임은 촬영(STREAMING) 중에만 나온다
    private val cameraStreaming = MutableStateFlow(false)

    // 송출 시작 허용 조건 ② 마지막 heartbeat 성공 시각(elapsedRealtime, 0=없음) —
    // 서버가 카메라 온라인으로 보는 기준(6분)과 맞춘다
    private val lastHeartbeatOkAt = MutableStateFlow(0L)

    // 받은 송출 토큰의 참고용 만료 예정 시각 — 상태 표시용, 송출 중단 기준 아님
    private var liveExpiresAtMillis = 0L

    /** 카메라 모드 촬영 상태 — Activity가 상태 전환마다 알린다 */
    fun setCameraStreaming(streaming: Boolean) {
        cameraStreaming.value = streaming
    }

    /** 분석 스레드에서 호출(PoseLandmarkerHelper.frameSink) — 송출 중일 때만 프레임을 넘긴다 */
    fun pushLiveFrame(bitmap: Bitmap) {
        liveVideoSource.pushFrame(bitmap)
    }

    // 페어링 코드 자동 재발급 루프 — 중복 실행 방지용 참조
    private var pairingCodeJob: Job? = null

    // Heartbeat 전송 루프 — 카메라 모드 켜져 있는 동안만 활성.
    // 백엔드 워치독이 6분 이상 미수신 시 오프라인 판정하므로 2분 주기면 3회 미스가 있어야 오프라인 낙인.
    private var heartbeatJob: Job? = null

    // ANDROID_ID는 Context가 필요해 Activity가 계산해서 넘겨준다
    fun setDeviceId(deviceId: String) {
        setState { copy(deviceId = deviceId) }
    }

    // 피보호자용 페어링 코드 자동 발급/재발급 루프.
    // 서버 TTL(15분)에 맞춰 만료 직전에 새 코드로 교체하므로 사용자 조작 없이 항상 유효한 코드 유지.
    // 이미 도는 루프가 있으면 그대로 둔다 — 재시작하면 백오프가 풀린다(아래 참고).
    //
    // 시작 전에 my-guardian 을 조회해 이미 페어링됐으면 코드 발급 자체를 건너뛴다. 백엔드 정책상
    // 이미 페어링된 elder 는 issuePairingCode 가 거부되므로, 어차피 발급되지도 않을 요청을
    // 반복하며 rate limit 을 소진하는 걸 방지.
    fun startPairingCodeAutoRefresh(userId: String) {
        if (userId.isBlank()) return
        // 화면 재생성 시에도 onCreate에서 다시 호출된다. 그때마다 새로 시작하면 실패 횟수가
        // 0으로 돌아가 백오프가 풀리고, 한도가 소진된 상태에서 즉시 재요청하게 된다.
        if (pairingCodeJob?.isActive == true) return
        pairingCodeJob = viewModelScope.launch {
            // 진입 시점 페어링 확인 — 이미 연결된 상태면 코드 발급 루프 자체를 안 돌린다.
            if (checkAlreadyPaired(userId)) return@launch

            var failures = 0
            while (isActive) {
                val ttlSeconds = fetchAndUpdatePairingCode(userId)
                val delayMs = if (ttlSeconds > 0) {
                    failures = 0
                    // TTL 만료 직전(-10초 안전 여유)에 재발급
                    (ttlSeconds - 10).coerceAtLeast(10) * 1000L
                } else {
                    // 재발급은 만료 10초 전에 돈다. 실패하면 화면의 코드도 곧 만료되므로 지운다 —
                    // 남겨두면 어르신이 죽은 코드를 불러주고 보호자는 이유 없이 계속 실패한다.
                    setState { copy(pairingCode = null) }
                    // 실패는 대개 발급 한도 초과다. 고정 간격으로 계속 두드리면 한도가 풀리지
                    // 않은 채 요청만 쌓이므로 간격을 늘려가며 재시도한다 (30초 → 최대 10분).
                    failures++
                    (RETRY_BASE_MS * (1L shl (failures - 1).coerceAtMost(5))).coerceAtMost(RETRY_MAX_MS)
                }
                delay(delayMs)
            }
        }
    }

    // 연결 상태 재판정 — 승인 성립·해제·코드 소진(연결 요청 도착) 시. my-guardian 조회부터 다시 돈다.
    // 백오프가 풀리지만 푸시 계기라 드물다
    fun refreshPairing(userId: String) {
        pairingCodeJob?.cancel()
        pairingCodeJob = null
        startPairingCodeAutoRefresh(userId)
    }

    // my-guardian 조회로 페어링 여부 확인. 성공 시 UI 상태 반영하고 true 리턴.
    // 네트워크 실패는 "판정 불가"로 보고 false 리턴 — 코드 발급을 시도해 서버 검증에 맡긴다.
    private suspend fun checkAlreadyPaired(userId: String): Boolean {
        return try {
            val response = ApiClient.api.getMyGuardian(userId)
            val body = response.body()
            if (response.isSuccessful && body?.success == true) {
                val guardian = body.data
                if (guardian != null) {
                    setState {
                        copy(
                            isPaired = true,
                            pairedGuardianUserId = guardian.userId,
                            pairedGuardianName = guardian.name,
                            pairingCode = null,
                        )
                    }
                    true
                } else {
                    // 재판정 경로 — 다른 쪽에서 해제됐으면 연결 표시를 걷는다
                    setState { copy(isPaired = false, pairedGuardianUserId = null, pairedGuardianName = null) }
                    false
                }
            } else false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    // 한 번의 코드 발급 요청 — TTL 초 반환, 실패 시 -1
    private suspend fun fetchAndUpdatePairingCode(userId: String): Long {
        return try {
            val response = ApiClient.api.issuePairingCode(userId)
            val body = response.body()
            if (response.isSuccessful && body?.success == true && body.data != null) {
                setState { copy(pairingCode = body.data.code) }
                body.data.expiresInSeconds
            } else {
                if (BuildConfig.DEBUG) Log.w("CameraMode", "페어링 코드 발급 실패 code=${response.code()}")
                -1L
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w("CameraMode", "페어링 코드 발급 예외", e)
            -1L
        }
    }

    // 보호자 홈 조회용 기기 등록 — 서버 upsert라 재진입 시 중복 없음
    fun registerDevice(userId: String, deviceId: String, deviceName: String) {
        if (userId.isBlank() || deviceId.isBlank()) return
        viewModelScope.launch {
            try {
                ApiClient.aiApi.registerDevice(
                    userId,
                    DeviceRegisterRequest(deviceId = deviceId, deviceName = deviceName)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 등록 실패해도 촬영은 무관 — 보호자 홈 기기 ID만 공란
                if (BuildConfig.DEBUG) Log.w("CameraMode", "기기 등록 실패", e)
            }
        }
    }

    // 카메라 모드 켜져 있는 동안 서버에 살아있음을 알린다. 절전모드 여부도 함께 전송 —
    // 백엔드가 판정 신뢰도 표시나 워치독 처리에 활용 가능.
    // 이미 도는 루프가 있으면 재시작 안 함(중복 요청 방지).
    fun startHeartbeat(isPowerSaveMode: () -> Boolean) {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val response = ApiClient.api.heartbeat(HeartbeatRequest(powerSaveMode = isPowerSaveMode()))
                    // 서버에 실제로 기록된 경우만 — 실시간 영상 송출 시작 허용 기준
                    if (response.isSuccessful) lastHeartbeatOkAt.value = SystemClock.elapsedRealtime()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // heartbeat 실패는 사용자에게 노출하지 않는다 — 다음 사이클에서 자연 복구.
                    if (BuildConfig.DEBUG) Log.w("CameraMode", "heartbeat 실패", e)
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    /**
     * 실시간 영상 송출 요청 감시 — 수신함([LiveRequestInbox])에 요청이 들어오면 꺼내 송출 토큰을 받는다.
     *
     * 송출 시작은 아래가 모두 맞을 때만 — 아니면 요청을 꺼내지 않고 만료 전까지 수신함에 둔다
     * (조건이 갖춰지는 순간 다시 평가된다):
     * - 카메라 모드가 촬영 중(분석 프레임이 나오는 상태)
     * - 마지막 heartbeat 성공이 [HEARTBEAT_FRESH_MS](6분, 서버 오프라인 판정 기준) 이내
     * 카메라 모드가 꺼져 있을 때 온 요청도 만료 전이면 진입 후 조건이 갖춰질 때 처리된다.
     * 이미 도는 감시가 있으면 재시작 안 함.
     */
    fun startLiveRequestWatch(context: Context) {
        if (liveRequestJob?.isActive == true) return
        if (liveRoomPublisher == null) {
            liveRoomPublisher = LiveRoomPublisher(context.applicationContext, viewModelScope, liveRoomListener)
        }
        liveRequestJob = viewModelScope.launch {
            // StateFlow 조합이라 처리 중에 새 요청·조건 변화가 와도 끝난 뒤 최신 값으로 다시 평가한다
            combine(LiveRequestInbox.pending, cameraStreaming, lastHeartbeatOkAt) { pending, streaming, heartbeatAt ->
                Triple(pending, streaming, heartbeatAt)
            }.collect { (pending, streaming, heartbeatAt) ->
                if (pending == null) return@collect
                if (_livePublishState.value != LivePublishState.Idle) return@collect
                if (!streaming) {
                    if (BuildConfig.DEBUG) Log.d("CameraMode", "송출 요청 보류 — 촬영 중 아님")
                    return@collect
                }
                if (!isHeartbeatFresh(heartbeatAt)) {
                    if (BuildConfig.DEBUG) Log.d("CameraMode", "송출 요청 보류 — heartbeat 6분 초과·미기록")
                    return@collect
                }
                val request = LiveRequestInbox.take() ?: return@collect
                fetchPublishToken(request)
            }
        }
    }

    private fun isHeartbeatFresh(heartbeatAt: Long): Boolean =
        heartbeatAt > 0L && SystemClock.elapsedRealtime() - heartbeatAt <= HEARTBEAT_FRESH_MS

    private suspend fun fetchPublishToken(request: LiveRequest) {
        _livePublishState.value = LivePublishState.FetchingToken
        // 토큰을 받는 동안 같은 세션의 재요청이 쌓이지 않게 송출 중으로 표시
        LiveRequestInbox.isPublishing = true
        when (val result = LivePublishTokenSource.fetch(request)) {
            is PublishTokenResult.Success -> {
                liveExpiresAtMillis = result.credentials.expiresAtMillis
                _livePublishState.value = LivePublishState.Ready(result.credentials)
                // 접속이 끝나지 않은 채 세션이 만료되는 경우 대비 — 접속되면 해제한다
                scheduleReadyExpiry(result.credentials)
                liveRoomPublisher?.connect(result.credentials) ?: resetLivePublish()
            }
            else -> {
                if (BuildConfig.DEBUG) Log.w("CameraMode", "송출 토큰 미발급 — $result")
                resetLivePublish()
            }
        }
    }

    private fun scheduleReadyExpiry(credentials: LivePublishCredentials) {
        liveReadyExpiryJob?.cancel()
        liveReadyExpiryJob = viewModelScope.launch {
            delay((credentials.expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0L))
            val state = _livePublishState.value
            if (state is LivePublishState.Ready && state.credentials == credentials) leaveLiveRoom()
        }
    }

    private val liveRoomListener = object : LiveRoomPublisher.Listener {
        // 접속 완료 — 이후 송출은 자체 타이머로 멈추지 않는다. 중단은 LiveKit 연결 끊김(서버의 방 삭제:
        // 만료·보호자 종료·해제·동의 철회) 또는 카메라 모드 종료뿐. 보호자 자동 연장은 피보호자에게
        // 알리지 않으므로 expires_at이 지나도 방이 살아 있으면 계속 송출해야 한다.
        override fun onConnected(room: Room) {
            liveReadyExpiryJob?.cancel()
            liveReadyExpiryJob = null
            liveRoom = room
            val roomName = room.name.orEmpty()
            val expiresAt = liveExpiresAtMillis
            _livePublishState.value = LivePublishState.Connected(roomName, expiresAt)
            viewModelScope.launch {
                if (liveVideoSource.publish(room)) {
                    // 송출 등록 사이에 방이 끊겼으면 상태를 되살리지 않는다
                    if (liveRoom === room) {
                        _livePublishState.value = LivePublishState.Publishing(roomName, expiresAt)
                        if (BuildConfig.DEBUG) {
                            val remainSec = (expiresAt - System.currentTimeMillis()) / 1000
                            Log.d("CameraMode", "송출 시작 — 참고용 만료까지 ${remainSec}초(연장 시 방 유지)")
                        }
                    }
                } else if (liveRoom === room) {
                    // 영상을 못 보내면 보호자는 기다리다 실패한다 — 방에서 나와 다음 요청을 받는다
                    leaveLiveRoom()
                }
            }
        }

        // 접속 실패·서버의 방 삭제 — 다음 live_request를 받을 수 있게 비운다
        override fun onEnded(reason: String) {
            if (BuildConfig.DEBUG) Log.d("CameraMode", "송출 종료 — $reason")
            resetLivePublish()
        }
    }

    // 우리 쪽에서 송출 종료 — 영상 트랙을 먼저 내리고(reset) 방을 해제한다
    private fun leaveLiveRoom() {
        resetLivePublish()
        liveRoomPublisher?.disconnect()
    }

    private fun resetLivePublish() {
        liveReadyExpiryJob?.cancel()
        liveReadyExpiryJob = null
        liveVideoSource.stop()
        liveRoom = null
        liveExpiresAtMillis = 0L
        _livePublishState.value = LivePublishState.Idle
        LiveRequestInbox.isPublishing = false
    }

    override fun onCleared() {
        super.onCleared()
        // 카메라 모드 종료 — 방에서 나가고, 송출 중 표시가 남아 다음 카메라 모드가 요청을 못 받는 일이 없게 비운다
        leaveLiveRoom()
        liveRoomPublisher = null
    }

    // 페어링 해제 — 양방향(피보호자·보호자 어느 쪽에서 호출해도 동작).
    // 성공 시 콜백으로 UI 갱신(코드 오버레이 다시 표시 등)을 알리고, 실패는 콜백에 예외 메시지 전달.
    fun unpair(userId: String, counterpartUserId: String, onResult: (Boolean, String?) -> Unit) {
        if (userId.isBlank() || counterpartUserId.isBlank()) {
            onResult(false, "연결 정보를 확인할 수 없어요.")
            return
        }
        viewModelScope.launch {
            try {
                val response = ApiClient.api.unpair(userId, counterpartUserId)
                if (response.isSuccessful && response.body()?.success == true) {
                    // 해제 후엔 다시 미연결 상태 — 코드 오버레이 다시 나타남.
                    setState { copy(isPaired = false, pairedGuardianUserId = null, pairedGuardianName = null) }
                    // 자동 재발급 루프 재시작 유도용으로 기존 job 취소
                    pairingCodeJob?.cancel()
                    pairingCodeJob = null
                    onResult(true, null)
                } else {
                    onResult(false, response.body()?.message ?: "연결 해제에 실패했어요.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onResult(false, "네트워크 오류가 발생했어요.")
            }
        }
    }

    private inline fun setState(update: CameraModeUiState.() -> CameraModeUiState) {
        _uiState.value = (_uiState.value ?: CameraModeUiState()).update()
    }

    private companion object {
        const val RETRY_BASE_MS = 30_000L
        const val RETRY_MAX_MS = 600_000L
        // 2분 — 백엔드 오프라인 임계(6분) 대비 3배 여유. 배터리 소모 vs 감지 지연 트레이드오프에서
        // 안전 서비스 특성상 감지 지연 최소화 쪽에 무게.
        const val HEARTBEAT_INTERVAL_MS = 120_000L
        // 서버 HeartbeatWatchdogJob.OFFLINE_THRESHOLD(6분)와 같은 기준 — 넘으면 송출 시작 보류
        const val HEARTBEAT_FRESH_MS = 6 * 60_000L
    }
}
