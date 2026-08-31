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

    // 촬영 종료 감지용 — 서버 점수는 촬영이 끝나도 그대로 남아있어서 값만 봐서는 구분이 안 된다.
    // 대신 updated_at이 더 이상 바뀌지 않는지를 본다.
    private var lastUpdatedAt: String? = null
    private var staleTicks = 0

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

        // 화면을 다시 열 때마다 처음부터 판단한다 — 이전 방문의 시각을 그대로 쓰면 오판한다
        lastUpdatedAt = null
        staleTicks = 0

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

    // 서버의 realtime_data는 촬영이 끝나도 마지막 값이 그대로 남는다.
    // 그래서 점수만 보면 촬영 중인지 알 수 없고, updated_at이 계속 바뀌는지로 판단한다.
    // 서버·기기 시계를 비교하지 않는 이유: 서버가 타임존 없는 문자열을 내려줘 시차만큼 어긋난다.
    private fun applyFreshness(score: Int, updatedAt: String?) {
        // 서버가 updated_at을 주지 않으면 판단 근거가 없으므로 종전대로 동작시킨다
        if (updatedAt == null) {
            applyRiskScore(score)
            return
        }
        when {
            // 첫 응답만으로는 갱신 중인지 멈춰 있는지 알 수 없다 — 다음 응답과 비교해야 한다
            lastUpdatedAt == null -> {
                lastUpdatedAt = updatedAt
                setState { copy(connectionState = ConnectionState.CONNECTING, riskScore = score) }
            }
            updatedAt != lastUpdatedAt -> {
                lastUpdatedAt = updatedAt
                staleTicks = 0
                applyRiskScore(score)
            }
            else -> {
                staleTicks++
                if (staleTicks >= STALE_TICKS_LIMIT) {
                    setState { copy(connectionState = ConnectionState.STANDBY, riskScore = score) }
                    // 다음 촬영에서 다시 위험에 진입하면 알림을 띄워야 하므로 등급 기억을 비운다
                    lastRiskLevel = null
                }
            }
        }
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

        // updated_at이 이만큼 연속으로 그대로면 촬영이 끝난 것으로 본다 (5초 × 3 = 15초)
        private const val STALE_TICKS_LIMIT = 3

        // 읽음 처리가 서버에 반영되기를 기다려주는 시간
        private const val READ_SYNC_GRACE_MS = 3_000L
    }
}
