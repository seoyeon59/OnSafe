package com.example.on_safe.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.FindIdRequest
import com.example.on_safe.network.errorMessage
import com.example.on_safe.network.isOk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// 아이디 찾기 화면 상태 — Activity는 값을 받아 화면 반영만
data class FindIdUiState(
    val isLoading: Boolean = false,
    val isFindEnabled: Boolean = true,
    val isResultVisible: Boolean = false,
    val foundId: String = ""
)

class FindIdViewModel : ViewModel() {

    private val _uiState = MutableLiveData(FindIdUiState())
    val uiState: LiveData<FindIdUiState> = _uiState

    // 1회성 토스트 — onToastShown()으로 소비, 화면 회전 시 재출력 방지
    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    // 이름+이메일이 일치하는 아이디를 바로 조회
    // TODO: [백엔드] findId에 rate limit이 없어 이름+이메일만으로 아이디 조회가 반복 가능.
    fun findId(name: String, email: String) {
        // 새로 조회할 때 이전 결과를 숨긴다 — 실패했는데 옛 아이디가 남아 보이지 않게
        setState { copy(isFindEnabled = false, isLoading = true, isResultVisible = false) }
        viewModelScope.launch {
            try {
                val response = ApiClient.api.findId(FindIdRequest(name = name, mail = email))
                val foundId = response.body()?.data?.userId
                if (response.isOk && foundId != null) {
                    setState { copy(isResultVisible = true, foundId = foundId) }
                } else {
                    _toastMessage.value = response.errorMessage("일치하는 아이디가 없습니다.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _toastMessage.value = "네트워크 오류가 발생했습니다."
            } finally {
                setState { copy(isFindEnabled = true, isLoading = false) }
            }
        }
    }

    fun onToastShown() {
        _toastMessage.value = null
    }

    private inline fun setState(update: FindIdUiState.() -> FindIdUiState) {
        _uiState.value = (_uiState.value ?: FindIdUiState()).update()
    }
}
