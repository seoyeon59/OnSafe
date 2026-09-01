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
    STANDBY("대기 중 — 카메라 기기 연결 필요", R.color.status_standby)
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

    // 마지막 갱신 시각 — 서버의 촬영 상태 제공 시 판정에 쓸 값
    private var lastUpdatedAt: String? = null

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
                    applyFreshness(score, body.data.updatedAt)
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

    // 촬영 종료 여부는 앱에서 판단 불가.
    // realtime_data는 종료 후에도 마지막 값이 남고 updated_at은 사람이 잡혔을 때만 갱신 —
    // 무인 감시의 빈 방이 정상 상태라 updated_at 정지를 종료로 보면 오판.
    // 따라서 데이터 존재 = 연결로 간주. 정확한 판정은 서버의 촬영 상태 제공 필요
    // (devices 컬렉션 status·last_seen 협의 중).
    private fun applyFreshness(score: Int, updatedAt: String?) {
        lastUpdatedAt = updatedAt
        applyRiskScore(score)
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
    }
}
