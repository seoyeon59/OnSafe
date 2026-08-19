package com.example.on_safe.ui.history

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.on_safe.data.repository.AccidentHistoryRepository
import com.example.on_safe.data.repository.RealAccidentHistoryRepository
import com.example.on_safe.network.ApiClient
import kotlinx.coroutines.launch

// 목록 + 정렬 상태 + 마지막 조회 실패 여부 (실패와 "진짜 빈 목록"을 구분하기 위해 별도 보관)
data class AccidentHistoryUiState(
    val entries: List<HistoryListItem.HistoryEntry> = emptyList(),
    val sort: SortOrder = SortOrder.NEWEST_FIRST,
    val lastLoadFailed: Boolean = false
)

// 1회성 토스트 메시지 — 조회/삭제/영상 URL 조회 실패, 삭제 성공 안내
data class HistoryToastEvent(val message: String)

// 영상 signed URL 조회 결과 — 시청(watch)/다운로드(download) 중 어느 요청이었는지 함께 전달
// (다운로드는 갤러리 저장을 위해 ContentResolver가 필요해 Activity가 마저 처리한다)
data class VideoUrlEvent(
    val url: String,
    val entry: HistoryListItem.HistoryEntry,
    val forDownload: Boolean
)

// 생성자에 기본값 파라미터를 두지 않는다 — by viewModels()의 기본 팩토리는 리플렉션으로
// "진짜 무인자 생성자"를 찾는데, Kotlin은 기본값 파라미터를 별도의 무인자 생성자로 노출하지
// 않아 런타임에 NoSuchMethodException이 난다. 그래서 필드 초기값으로 대신 지정한다.
class AccidentHistoryViewModel : ViewModel() {

    private val repository: AccidentHistoryRepository = RealAccidentHistoryRepository()

    private var rawEntries: List<HistoryListItem.HistoryEntry> = emptyList()

    private val _uiState = MutableLiveData(AccidentHistoryUiState())
    val uiState: LiveData<AccidentHistoryUiState> = _uiState

    private val _toastEvent = MutableLiveData<HistoryToastEvent?>()
    val toastEvent: LiveData<HistoryToastEvent?> = _toastEvent

    private val _videoUrlEvent = MutableLiveData<VideoUrlEvent?>()
    val videoUrlEvent: LiveData<VideoUrlEvent?> = _videoUrlEvent

    fun loadHistory(userId: String) {
        if (userId.isBlank()) return

        viewModelScope.launch {
            try {
                rawEntries = repository.getHistoryEntries(userId)
                setState { copy(entries = rawEntries, lastLoadFailed = false) }
            } catch (e: IllegalStateException) {
                setState { copy(lastLoadFailed = true) }
                _toastEvent.value = HistoryToastEvent(e.message ?: "사고 이력을 불러오지 못했습니다.")
            } catch (e: Exception) {
                setState { copy(lastLoadFailed = true) }
                _toastEvent.value = HistoryToastEvent("네트워크 오류로 사고 이력을 불러오지 못했습니다.")
            }
        }
    }

    fun setSort(sort: SortOrder) {
        setState { copy(sort = sort) }
    }

    fun fetchVideoUrl(userId: String, entry: HistoryListItem.HistoryEntry, forDownload: Boolean) {
        viewModelScope.launch {
            try {
                val response = ApiClient.api.getFallLogVideo(userId, entry.id)
                val body = response.body()
                val signedUrl = body?.data?.get("signed_url")
                if (response.isSuccessful && body?.success == true && signedUrl != null) {
                    _videoUrlEvent.value = VideoUrlEvent(signedUrl, entry, forDownload)
                } else {
                    _toastEvent.value = HistoryToastEvent(
                        ApiClient.parseErrorMessage(response.errorBody(), "영상을 불러올 수 없습니다.")
                    )
                }
            } catch (e: Exception) {
                _toastEvent.value = HistoryToastEvent("네트워크 오류로 영상을 불러올 수 없습니다.")
            }
        }
    }

    fun deleteEntry(userId: String, entry: HistoryListItem.HistoryEntry) {
        viewModelScope.launch {
            try {
                val response = ApiClient.api.deleteFallLog(userId, entry.id)
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    rawEntries = rawEntries.filter { it.id != entry.id }
                    setState { copy(entries = rawEntries, lastLoadFailed = false) }
                    _toastEvent.value = HistoryToastEvent("이력이 삭제되었습니다.")
                } else {
                    _toastEvent.value = HistoryToastEvent(
                        ApiClient.parseErrorMessage(response.errorBody(), "삭제에 실패했습니다.")
                    )
                }
            } catch (e: Exception) {
                _toastEvent.value = HistoryToastEvent("네트워크 오류로 삭제에 실패했습니다.")
            }
        }
    }

    fun onToastHandled() {
        _toastEvent.value = null
    }

    fun onVideoUrlHandled() {
        _videoUrlEvent.value = null
    }

    private inline fun setState(update: AccidentHistoryUiState.() -> AccidentHistoryUiState) {
        _uiState.value = (_uiState.value ?: AccidentHistoryUiState()).update()
    }
}
