package com.example.on_safe.ui.history

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.on_safe.data.repository.AccidentHistoryRepository
import com.example.on_safe.data.repository.RealAccidentHistoryRepository
import com.example.on_safe.data.repository.WardSource
import com.example.on_safe.network.ApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// 목록 + 정렬 상태 + 조회 실패 여부 — 실패와 "진짜 빈 목록"의 구분용
// notPaired: 연결된 피보호자 없음 — 조회 대상이 없어 빈 목록과 다른 안내가 필요
data class AccidentHistoryUiState(
    val entries: List<HistoryListItem.HistoryEntry> = emptyList(),
    val sort: SortOrder = SortOrder.NEWEST_FIRST,
    val lastLoadFailed: Boolean = false,
    val notPaired: Boolean = false
)

// 1회성 토스트 — 조회/삭제/영상 URL 실패, 삭제 성공 안내
data class HistoryToastEvent(val message: String)

// 영상 signed URL 조회 결과 — 시청/다운로드 요청 구분 포함
// 다운로드는 갤러리 저장에 ContentResolver가 필요해 Activity가 마저 처리
data class VideoUrlEvent(
    val url: String,
    val entry: HistoryListItem.HistoryEntry,
    val forDownload: Boolean
)

// 생성자 기본값 파라미터 금지 — by viewModels()의 무인자 생성자 탐색 실패 원인
class AccidentHistoryViewModel : ViewModel() {

    private val repository: AccidentHistoryRepository = RealAccidentHistoryRepository()

    private var rawEntries: List<HistoryListItem.HistoryEntry> = emptyList()

    // 사고 이력·영상·삭제의 조회 대상 — 로그인한 보호자 본인이 아니라 연결된 피보호자.
    // 목록 조회 때 확정해 같은 대상의 영상·삭제에 그대로 쓴다(목록과 대상이 어긋나지 않게).
    private var wardUserId: String? = null

    private val _uiState = MutableLiveData(AccidentHistoryUiState())
    val uiState: LiveData<AccidentHistoryUiState> = _uiState

    private val _toastEvent = MutableLiveData<HistoryToastEvent?>()
    val toastEvent: LiveData<HistoryToastEvent?> = _toastEvent

    private val _videoUrlEvent = MutableLiveData<VideoUrlEvent?>()
    val videoUrlEvent: LiveData<VideoUrlEvent?> = _videoUrlEvent

    fun loadHistory(guardianUserId: String) {
        if (guardianUserId.isBlank()) return

        viewModelScope.launch {
            try {
                // 화면 진입마다 재확인 — 다른 화면에서 연결·해제됐을 수 있다
                val ward = WardSource.fetchPairedWardUserId(guardianUserId)
                wardUserId = ward
                if (ward == null) {
                    rawEntries = emptyList()
                    setState { copy(entries = rawEntries, lastLoadFailed = false, notPaired = true) }
                    return@launch
                }
                rawEntries = repository.getHistoryEntries(ward)
                setState { copy(entries = rawEntries, lastLoadFailed = false, notPaired = false) }
            } catch (e: CancellationException) {
                throw e   // 화면 이탈에 의한 취소 — 조회 실패 처리 대상 아님
            } catch (e: IllegalStateException) {
                // 저장소가 서버 메시지를 담아 던지는 경로
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

    fun fetchVideoUrl(entry: HistoryListItem.HistoryEntry, forDownload: Boolean) {
        val ward = wardUserId ?: return   // 목록이 있으면 대상도 확정돼 있다
        viewModelScope.launch {
            try {
                val response = ApiClient.api.getFallLogVideo(ward, entry.id)
                val body = response.body()
                val signedUrl = body?.data?.signedUrl
                if (response.isSuccessful && body?.success == true && signedUrl != null) {
                    _videoUrlEvent.value = VideoUrlEvent(signedUrl, entry, forDownload)
                } else {
                    _toastEvent.value = HistoryToastEvent(
                        ApiClient.parseErrorMessage(response.errorBody(), "영상을 불러올 수 없습니다.")
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _toastEvent.value = HistoryToastEvent("네트워크 오류로 영상을 불러올 수 없습니다.")
            }
        }
    }

    // 서버가 연결된 보호자 삭제를 허용(연결 이후 이력 한정)
    fun deleteEntry(entry: HistoryListItem.HistoryEntry) {
        val ward = wardUserId ?: return
        viewModelScope.launch {
            try {
                val response = ApiClient.api.deleteFallLog(ward, entry.id)
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    // 재조회 없이 로컬 목록에서만 제거 — 목록 깜빡임 방지
                    rawEntries = rawEntries.filter { it.id != entry.id }
                    setState { copy(entries = rawEntries, lastLoadFailed = false) }
                    _toastEvent.value = HistoryToastEvent("이력이 삭제되었습니다.")
                } else {
                    _toastEvent.value = HistoryToastEvent(
                        ApiClient.parseErrorMessage(response.errorBody(), "삭제에 실패했습니다.")
                    )
                }
            } catch (e: CancellationException) {
                throw e
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
