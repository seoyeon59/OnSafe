package com.example.on_safe

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.on_safe.data.repository.NotificationRepository
import com.example.on_safe.data.repository.RealNotificationRepository
import com.example.on_safe.network.ApiClient
import com.example.on_safe.util.RiskScoreCardBinder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// 홈 연결 상태 — colorRes는 리소스 id라 Context 없이 참조 가능
enum class ConnectionState(val label: String, val colorRes: Int) {
    CONNECTED("기기 연결됨", R.color.status_normal),
    CONNECTING("연결 확인 중...", R.color.status_warning),
    FAILED("기기 연결 실패", R.color.status_danger),
    STANDBY("대기 중 — 카메라 기기 연결 필요", R.color.status_standby),

    // 프레임은 도착하나 추론이 실패 중 — 감지 기능 정지 상태라 danger 취급
    INFERENCE_ERROR("낙상 감지 일시 중단", R.color.status_danger),
    // 프레임은 도착하나 추론 결과만 정체 — 저사양 기기의 처리 지연
    SLOW("낙상 감지 처리 지연 중", R.color.status_warning),
    // 프레임 도착이 막 끊김 — STANDBY 확정 전 유예 구간
    RECONNECTING("연결 재확인 중", R.color.status_warning)
}

// 홈 화면 상태 — 연결 상태 + 최신 위험 점수(폴링 전이라 아직 없으면 null) + 미읽음 알림 유무
//                + 카메라 기기 ID(조회 전·미등록이면 null)
data class MainUiState(
    val connectionState: ConnectionState = ConnectionState.CONNECTING,
    val riskScore: Int? = null,
    val hasUnread: Boolean = false,
    val deviceId: String? = null
)

// 위험(DANGER) 진입 1회성 이벤트 — 감지 시점 점수/시각 스냅샷, Activity가 모달로 소비
data class FallAlertEvent(val score: Int, val detectedAtMillis: Long)

class MainViewModel : ViewModel() {

    private val _uiState = MutableLiveData(MainUiState())
    val uiState: LiveData<MainUiState> = _uiState

    private val _fallAlertEvent = MutableLiveData<FallAlertEvent?>()
    val fallAlertEvent: LiveData<FallAlertEvent?> = _fallAlertEvent

    private var pollingJob: Job? = null

    // DANGER 신규 진입 판별용 직전 등급
    private var lastRiskLevel: RiskScoreCardBinder.RiskLevel? = null

    // 직전 응답의 두 시각 — 정체 여부 판정 기준
    private var lastUpdatedAt: String? = null
    private var lastDeviceSeenAt: String? = null

    // 연속 정체 틱 수 (1틱 = POLLING_INTERVAL_MS)
    private var updatedAtStaleTicks = 0
    private var deviceSeenStaleTicks = 0

    private val notificationRepository: NotificationRepository = RealNotificationRepository()

    // 알림 화면에서 "모두 읽음" 상태로 돌아온 시각 — 서버 반영 지연 중 빨간 점 재점등 방지
    private var allReadAtMillis = 0L

    // userId 조회는 Context 필요 — Activity가 전달
    fun startPolling(userId: String) {
        stopPolling()

        if (userId.isBlank()) {
            setState { copy(connectionState = ConnectionState.STANDBY) }
            return
        }

        lastUpdatedAt = null
        lastDeviceSeenAt = null
        updatedAtStaleTicks = 0
        deviceSeenStaleTicks = 0

        // 첫 응답 전 직전 FAILED 잔상 제거
        setState { copy(connectionState = ConnectionState.CONNECTING) }

        refreshUnreadBadge(userId)
        refreshDeviceId(userId)

        pollingJob = viewModelScope.launch {
            while (isActive) {
                fetchRiskScoreOnce(userId)
                delay(POLLING_INTERVAL_MS)
            }
        }
    }

    // 알림 화면 진입해야만 뱃지가 갱신되던 문제 — 홈 복귀 시마다 직접 확인
    fun refreshUnreadBadge(userId: String) {
        if (userId.isBlank()) return
        viewModelScope.launch {
            val unread = try {
                notificationRepository.getNotifications(userId).any { it.isUnread }
            } catch (e: CancellationException) {
                throw e         // 코루틴 취소는 실패 아님 — 전파
            } catch (_: Exception) {
                return@launch   // 조회 실패 시 기존 표시를 유지한다
            }
            // "모두 읽음" 직후 서버 반영 지연 — 서버 값을 그대로 쓰면 빨간 점 재점등
            if (unread && System.currentTimeMillis() - allReadAtMillis < READ_SYNC_GRACE_MS) return@launch
            setState { copy(hasUnread = unread) }
        }
    }

    // 계정당 카메라 1대 전제로 첫 항목만 사용.
    // devices의 status·last_seen은 서버 미갱신이라 미사용 — 연결 표시는 위험 지수 폴링 담당
    private fun refreshDeviceId(userId: String) {
        viewModelScope.launch {
            val deviceId = try {
                ApiClient.aiApi.getDevices(userId).body()?.devices?.firstOrNull()?.deviceId
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return@launch   // 조회 실패 시 기존 표시를 유지한다
            }
            setState { copy(deviceId = deviceId) }
        }
    }

    // 알림 화면 복귀 직후 네트워크 왕복 없는 즉시 반영 경로
    fun setUnreadBadge(hasUnread: Boolean) {
        if (!hasUnread) allReadAtMillis = System.currentTimeMillis()
        setState { copy(hasUnread = hasUnread) }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    // 상태 4분류 — 전부 "연결 실패"로 뭉치면 원인 파악 불가
    //   404(데이터 없음) = 카메라가 아직 촬영 미시작 → STANDBY
    //   그 외 실패·네트워크 오류 = 서버 미도달 → FAILED
    private suspend fun fetchRiskScoreOnce(userId: String) {
        try {
            val response = ApiClient.api.getRiskScore(userId)
            val body = response.body()
            when {
                response.isSuccessful && body?.success == true && body.data != null -> {
                    val score = body.data.score.toInt().coerceIn(0, 100)
                    applyFreshness(score, body.data.level, body.data.updatedAt, body.data.deviceSeenAt)
                }
                response.code() == 404 -> {
                    setState { copy(connectionState = ConnectionState.STANDBY) }
                }
                else -> {
                    setState { copy(connectionState = ConnectionState.FAILED) }
                }
            }
        } catch (e: CancellationException) {
            // 화면 이탈 취소를 FAILED로 뭉칠 때의 "기기 연결 실패" 잔상 방지
            throw e
        } catch (_: Exception) {
            setState { copy(connectionState = ConnectionState.FAILED) }
        }
    }

    /**
     * 연결 상태 판정 — deviceSeenAt(프레임 도착)이 updatedAt(추론 성공)보다 상위 신호.
     *
     * 두 시각을 분리하지 않으면 "프레임 끊김"·"추론 실패"·"처리 지연"이 전부 STANDBY로
     * 뭉쳐, 감지 기능이 죽은 상황이 "아직 안 켰나보다"로 감춰짐.
     *
     * 우선순위: 프레임 정체 → 추론 오류 → 처리 지연 → 정상
     */
    private fun applyFreshness(score: Int, level: String?, updatedAt: String?, deviceSeenAt: String?) {
        // 구버전 서버 — 판정 근거가 없어 점수만으로 처리
        if (updatedAt == null) {
            applyRiskScore(score)
            return
        }

        deviceSeenStaleTicks = if (deviceSeenAt != null && deviceSeenAt == lastDeviceSeenAt) {
            deviceSeenStaleTicks + 1
        } else 0
        updatedAtStaleTicks = if (updatedAt == lastUpdatedAt) updatedAtStaleTicks + 1 else 0
        lastDeviceSeenAt = deviceSeenAt
        lastUpdatedAt = updatedAt

        // 2) 프레임은 오는데 추론이 실패 중 — 감지 기능 정지.
        // deviceSeenAt과 무관하게 서버가 명시적으로 알려주는 신호라 항상 신뢰 가능
        if (level == LEVEL_INFERENCE_ERROR) {
            setStalled(ConnectionState.INFERENCE_ERROR)
            return
        }

        // 하트비트 미지원 서버, 또는 하트비트가 무인 상태를 덮지 못하는 동안은 정체 판정 보류
        if (deviceSeenAt == null || !HEARTBEAT_COVERS_IDLE) {
            applyRiskScore(score)
            return
        }

        // 1) 프레임 자체가 안 들어옴 — WiFi 재연결 유예를 두고 확정
        if (deviceSeenStaleTicks >= RECONNECTING_TICKS) {
            val stalled = if (deviceSeenStaleTicks >= DEVICE_STANDBY_TICKS) {
                ConnectionState.STANDBY
            } else {
                ConnectionState.RECONNECTING
            }
            setStalled(stalled)
            return
        }

        // 3) 프레임은 오는데 추론 결과만 정체 — 저사양 기기의 처리 지연
        if (updatedAtStaleTicks >= SLOW_TICKS) {
            setStalled(ConnectionState.SLOW)
            return
        }

        applyRiskScore(score)
    }

    // 정체·오류 상태의 직전 점수 제거 — 낡은 값이 현재 안전 상태로 오독됨
    private fun setStalled(state: ConnectionState) {
        setState { copy(connectionState = state, riskScore = null) }
    }

    private fun applyRiskScore(score: Int) {
        setState { copy(connectionState = ConnectionState.CONNECTED, riskScore = score) }

        val currentLevel = RiskScoreCardBinder.RiskLevel.fromScore(score)
        // DANGER 신규 진입 시에만 이벤트 발생
        if (currentLevel == RiskScoreCardBinder.RiskLevel.DANGER &&
            lastRiskLevel != RiskScoreCardBinder.RiskLevel.DANGER
        ) {
            _fallAlertEvent.value = FallAlertEvent(score, System.currentTimeMillis())
        }
        lastRiskLevel = currentLevel
    }

    // 모달 표시 후 Activity가 호출 — 재구독(화면 회전 등) 시 재표시 방지
    fun onFallAlertHandled() {
        _fallAlertEvent.value = null
    }

    private inline fun setState(update: MainUiState.() -> MainUiState) {
        _uiState.value = (_uiState.value ?: MainUiState()).update()
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }

    companion object {
        private const val POLLING_INTERVAL_MS = 5_000L

        // 서버의 읽음 처리 반영 대기 시간
        private const val READ_SYNC_GRACE_MS = 3_000L

        // 서버가 추론 실패를 알리는 level 값 (백엔드 INFERENCE_ERROR_LEVEL과 동일)
        private const val LEVEL_INFERENCE_ERROR = "오류"

        /*
         * deviceSeenAt 기반 정체 판정 활성 여부.
         *
         * 서버 하트비트는 WS 프레임 수신 시 갱신되나, 앱은 관절이 잡힐 때만 프레임 전송
         * (PoseLandmarkerHelper: poses.isEmpty() → return). 빈 방이 정상인 무인 감시에서
         * deviceSeenAt이 멈춰 촬영 종료로 오판 — 커밋 1ef5a50에서 되돌린 문제와 동일.
         *
         * 무인 상태의 하트비트 유지에 서버·앱 합의가 끝나면 true로 전환.
         */
        private const val HEARTBEAT_COVERS_IDLE = false

        // 정체 판정 임계 틱 — 폴링 5초 기준.
        // 하트비트 간격도 5초라 1틱은 지터만으로도 겹침 — 최소 2틱부터 정체로 본다
        private const val RECONNECTING_TICKS = 2    // 10초 — 유예 시작
        private const val SLOW_TICKS = 3            // 15초 — 처리 지연
        private const val DEVICE_STANDBY_TICKS = 6  // 30초 — WiFi 재연결 고려한 확정
    }
}
