package com.example.on_safe

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.on_safe.network.ApiClient
import com.example.on_safe.util.RiskScoreCardBinder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// 홈 화면 연결 상태 — colorRes는 리소스 id 값만 들고 있어 Context 없이도 참조 가능
enum class ConnectionState(val label: String, val colorRes: Int) {
    CONNECTED("기기 연결됨", R.color.status_normal),
    CONNECTING("연결중...", R.color.status_warning),
    FAILED("기기 연결 실패", R.color.status_danger),
    STANDBY("대기 중", R.color.status_standby)
}

// 홈 화면 상태 — 연결 상태 + 최신 위험 점수(폴링 전이라 아직 없으면 null)
data class MainUiState(
    val connectionState: ConnectionState = ConnectionState.CONNECTING,
    val riskScore: Int? = null
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

    // userId 조회(TokenManager)는 Context가 필요해 Activity가 넘겨준다
    fun startPolling(userId: String) {
        stopPolling()

        if (userId.isBlank()) {
            setState { copy(connectionState = ConnectionState.STANDBY) }
            return
        }

        pollingJob = viewModelScope.launch {
            while (isActive) {
                fetchRiskScoreOnce(userId)
                delay(POLLING_INTERVAL_MS)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private suspend fun fetchRiskScoreOnce(userId: String) {
        try {
            val response = ApiClient.api.getRiskScore(userId)
            val body = response.body()
            if (response.isSuccessful && body?.success == true && body.data != null) {
                val score = body.data.score.toInt().coerceIn(0, 100)
                applyRiskScore(score)
            } else {
                setState { copy(connectionState = ConnectionState.FAILED) }
            }
        } catch (_: Exception) {
            setState { copy(connectionState = ConnectionState.FAILED) }
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
    }
}
