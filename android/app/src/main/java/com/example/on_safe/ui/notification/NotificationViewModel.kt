package com.example.on_safe.ui.notification

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.on_safe.data.repository.NotificationRepository
import com.example.on_safe.data.repository.RealNotificationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// loadFailed로 "조회 실패"와 "알림 없음"을 구분해 빈 화면 문구를 다르게 보여준다 (사고이력과 동일 패턴)
//
// TODO: 화면 문구는 "최근 7일간"인데 실제 컷오프 없음 (서버 보관 정책 30일).
//       문구 수정 / 클라이언트 필터 / 서버 조회 파라미터 중 택일 — 기획·백엔드 확인 필요
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
                // 화면 진입 시 WARNING은 일괄 읽음 처리
                val unreadWarnings = fetched.filter { it.isUnreadWarning() }
                setState {
                    copy(
                        items = fetched.map { if (it.isUnreadWarning()) it.copy(isUnread = false) else it },
                        loadFailed = false
                    )
                }
                unreadWarnings.forEach { confirm(userId, it.id) }
            // CancellationException은 IllegalStateException의 하위 타입 — 순서를 바꾸면
            // 화면 이탈로 인한 취소가 오류 토스트로 새어 나온다
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                // 저장소가 parseErrorMessage를 거쳐 던지는 문구 — 사용자 노출 가능
                fail(e.message ?: LOAD_FAILED_MSG)
            } catch (_: Exception) {
                // 네트워크 예외 원문에는 서버 주소가 섞여 있어 그대로 노출 불가
                fail(NETWORK_ERROR_MSG)
            }
        }
    }

    // FALL 모달에서 확인/119를 눌렀을 때 호출 — 해당 항목만 읽음 처리
    fun markFallItemRead(userId: String, logId: String) {
        val current = _uiState.value ?: return
        if (current.items.none { it.id == logId && it.isUnread }) return
        setState { copy(items = items.map { if (it.id == logId) it.copy(isUnread = false) else it }) }
        confirm(userId, logId)
    }

    fun hasUnreadItems(): Boolean = _uiState.value?.items?.any { it.isUnread } == true

    private fun confirm(userId: String, logId: String) {
        viewModelScope.launch {
            try {
                repository.confirmNotification(userId, logId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 실패해도 로컬 읽음 표시는 유지 — 다음 조회 때 서버 값으로 재동기화됨
            }
        }
    }

    fun onToastHandled() {
        _toastEvent.value = null
    }

    private fun fail(message: String) {
        setState { copy(loadFailed = true) }
        _toastEvent.value = NotificationToastEvent(message)
    }

    private fun NotificationItem.isUnreadWarning() =
        type == NotificationType.WARNING && isUnread

    private inline fun setState(update: NotificationUiState.() -> NotificationUiState) {
        _uiState.value = (_uiState.value ?: NotificationUiState()).update()
    }

    private companion object {
        const val LOAD_FAILED_MSG = "알림 내역을 불러오지 못했습니다."
        const val NETWORK_ERROR_MSG = "네트워크 오류가 발생했습니다."
    }
}
