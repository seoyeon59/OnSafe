package com.example.on_safe.ui.notification

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.on_safe.data.repository.NotificationRepository
import com.example.on_safe.data.repository.RealNotificationRepository
import kotlinx.coroutines.launch

// items가 유일한 진실의 원천 — 어댑터는 이 상태를 그대로 받아 그리기만 하는 렌더러다.
// loadFailed는 "조회 실패"와 "최근 7일간 알림 없음"을 구분해서 다른 빈 화면 문구를 보여주기 위함
// (사고이력 화면과 동일 패턴).
//
// TODO: 알림 화면 안내 문구는 "최근 7일간"이지만, 현재 목록은 사고이력과 같은
//       GET /api/fall-logs/{userId}를 그대로 재사용하고 있어 7일로 잘라내는 로직이 없다.
//       (서버 보관 정책은 30일 고정 — API 스펙 GET /api/settings/retention 참고)
//       → 7일 컷오프를 클라이언트에서 걸지, 안내 문구를 30일로 바꿀지, 알림 전용 API를
//         따로 둘지 팀 확인 후 결정 필요.
data class NotificationUiState(
    val items: List<NotificationItem> = emptyList(),
    val loadFailed: Boolean = false
)

data class NotificationToastEvent(val message: String)

class NotificationViewModel : ViewModel() {

    private val repository: NotificationRepository = RealNotificationRepository()

    private val _uiState = MutableLiveData(NotificationUiState())
    val uiState: LiveData<NotificationUiState> = _uiState

    private val _toastEvent = MutableLiveData<NotificationToastEvent?>()
    val toastEvent: LiveData<NotificationToastEvent?> = _toastEvent

    fun load(userId: String) {
        if (userId.isBlank()) return
        viewModelScope.launch {
            try {
                val fetched = repository.getNotifications(userId)
                // 화면 진입 시 WARNING은 일괄 읽음 처리 (원래 로직과 동일)
                val warningsToConfirm = fetched.filter { it.type == NotificationType.WARNING && it.isUnread }
                val initial = fetched.map {
                    if (it.type == NotificationType.WARNING && it.isUnread) it.copy(isUnread = false) else it
                }
                setState { copy(items = initial, loadFailed = false) }
                warningsToConfirm.forEach { confirm(userId, it.id) }
            } catch (e: Exception) {
                setState { copy(loadFailed = true) }
                _toastEvent.value = NotificationToastEvent(e.message ?: "알림 내역을 불러오지 못했습니다.")
            }
        }
    }

    // FALL 모달에서 확인/119를 눌렀을 때 호출 — 해당 항목만 읽음 처리
    fun markFallItemRead(userId: String, logId: String) {
        val current = _uiState.value ?: return
        if (current.items.none { it.id == logId && it.isUnread }) return
        val updated = current.items.map { if (it.id == logId) it.copy(isUnread = false) else it }
        setState { copy(items = updated) }
        confirm(userId, logId)
    }

    fun hasUnreadItems(): Boolean = _uiState.value?.items?.any { it.isUnread } == true

    private fun confirm(userId: String, logId: String) {
        viewModelScope.launch {
            try {
                repository.confirmNotification(userId, logId)
            } catch (_: Exception) {
                // 실패해도 로컬 읽음 표시는 유지 — 다음 조회 때 서버 값으로 재동기화됨
            }
        }
    }

    fun onToastHandled() {
        _toastEvent.value = null
    }

    private inline fun setState(update: NotificationUiState.() -> NotificationUiState) {
        _uiState.value = (_uiState.value ?: NotificationUiState()).update()
    }
}
