package com.example.on_safe

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.on_safe.data.repository.NotificationRepository
import com.example.on_safe.data.repository.RealNotificationRepository
import com.example.on_safe.network.ApiClient
import com.example.on_safe.util.RiskScoreCardBinder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// 홈 화면 연결 상태 — colorRes는 리소스 id 값만 들고 있어 Context 없이도 참조 가능
enum class ConnectionState(val label: String, val colorRes: Int) {
    CONNECTED("기기 연결됨", R.color.status_normal),
    CONNECTING("연결 확인 중...", R.color.status_warning),
    FAILED("기기 연결 실패", R.color.status_danger),
    STANDBY("대기 중 — 카메라 기기 연결 필요", R.color.status_standby)
}

// 홈 화면 상태 — 연결 상태 + 최신 위험 점수(폴링 전이라 아직 없으면 null) + 미읽음 알림 유무
data class MainUiState(
    val connectionState: ConnectionState = ConnectionState.CONNECTING,
    val riskScore: Int? = null,
    val hasUnread: Boolean = false
)

// 위험(DANGER) 진입 1회성 이벤트 — 감지 시점 점수/시각 스냅샷, Activity가 모달로 소비
data class FallAlertEvent(val score: Int, val detectedAtMillis: Long)

class MainViewModel : ViewModel() {

    private val _uiState = MutableLiveData(MainUiState())
    val uiState: LiveData<MainUiState> = _uiState

    private val _fallAlertEvent = MutableLiveData<FallAlertEvent?>()
    val fallAlertEvent: LiveData<FallAlertEvent?> = _fallAlertEvent

    private var pollingJob: Job? = null

    // DANGER 진입 시점에만 이벤트를 발생시키기 위해 직전 등급을 기억
    private var lastRiskLevel: RiskScoreCardBinder.RiskLevel? = null

    // 마지막으로 받은 갱신 시각 — 서버가 촬영 상태를 알려주게 되면 판정에 쓸 값
    private var lastUpdatedAt: String? = null

    private val notificationRepository: NotificationRepository = RealNotificationRepository()

    // 알림 화면에서 "모두 읽음" 상태로 돌아온 시각 — 서버 반영 지연 중 빨간 점 재점등 방지
    private var allReadAtMillis = 0L

    // userId 조회(TokenManager)는 Context가 필요해 Activity가 넘겨준다
    fun startPolling(userId: String) {
        stopPolling()

        if (userId.isBlank()) {
            setState { copy(connectionState = ConnectionState.STANDBY) }
            return
        }

        lastUpdatedAt = null

        refreshUnreadBadge(userId)

        pollingJob = viewModelScope.launch {
            while (isActive) {
                fetchRiskScoreOnce(userId)
                delay(POLLING_INTERVAL_MS)
            }
        }
    }

    // 알림 화면에 들어갔다 나와야만 뱃지가 갱신되던 문제 — 홈에 올라올 때마다 직접 확인한다
    fun refreshUnreadBadge(userId: String) {
        if (userId.isBlank()) return
        viewModelScope.launch {
            val unread = try {
                notificationRepository.getNotifications(userId).any { it.isUnread }
            } catch (_: Exception) {
                return@launch   // 조회 실패 시 기존 표시를 유지한다
            }
            // 방금 "모두 읽음"으로 표시했는데 서버의 읽음 처리가 아직 반영되지 않았을 수 있다.
            // 이때 서버 값을 그대로 쓰면 껐던 빨간 점이 다시 켜졌다가 꺼진다.
            if (unread && System.currentTimeMillis() - allReadAtMillis < READ_SYNC_GRACE_MS) return@launch
            setState { copy(hasUnread = unread) }
        }
    }

    // 알림 화면에서 돌아온 직후 네트워크 왕복 없이 즉시 반영하기 위한 경로
    fun setUnreadBadge(hasUnread: Boolean) {
        if (!hasUnread) allReadAtMillis = System.currentTimeMillis()
        setState { copy(hasUnread = hasUnread) }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    // 상태를 4가지로 구분한다 — 전부 "연결 실패"로 뭉치면 사용자가 원인을 알 수 없다.
    //   404(데이터 없음) = 카메라 기기가 아직 촬영을 시작하지 않은 것 → STANDBY
    //   그 외 실패/네트워크 오류 = 서버에 닿지 못한 것 → FAILED
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
        } catch (_: Exception) {
            setState { copy(connectionState = ConnectionState.FAILED) }
        }
    }

    // 촬영 종료 여부는 앱에서 판단할 수 없다.
    //
    // 서버 realtime_data는 촬영이 끝나도 마지막 값이 남고, updated_at은 "사람이 화면에
    // 잡혔을 때"만 갱신된다. 카메라 폰은 사람이 없으면 아무것도 보내지 않기 때문이다.
    // 무인 감시에서는 빈 방이 정상 상태이므로, updated_at이 멈춘 것을 촬영 종료로 보면
    // 카메라가 멀쩡히 돌아가는데도 대기 중으로 표시된다.
    //
    // 따라서 데이터가 있으면 연결된 것으로 본다. 정확한 판정은 서버가 촬영 상태를
    // 알려줘야 가능하다(devices 컬렉션의 status·last_seen 필드 활용 협의 중).
    private fun applyFreshness(score: Int, updatedAt: String?) {
        lastUpdatedAt = updatedAt
        applyRiskScore(score)
    }

    private fun applyRiskScore(score: Int) {
        setState { copy(connectionState = ConnectionState.CONNECTED, riskScore = score) }

        val currentLevel = RiskScoreCardBinder.RiskLevel.fromScore(score)
        // 이전 등급이 DANGER 미만이었다가 DANGER로 진입한 경우에만 이벤트 발생
        if (currentLevel == RiskScoreCardBinder.RiskLevel.DANGER &&
            lastRiskLevel != RiskScoreCardBinder.RiskLevel.DANGER
        ) {
            _fallAlertEvent.value = FallAlertEvent(score, System.currentTimeMillis())
        }
        lastRiskLevel = currentLevel
    }

    // 모달을 띄운 뒤 Activity가 호출 — 재구독(화면 회전 등) 시 모달 재표시 방지
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

        // 읽음 처리가 서버에 반영되기를 기다려주는 시간
        private const val READ_SYNC_GRACE_MS = 3_000L
    }
}
