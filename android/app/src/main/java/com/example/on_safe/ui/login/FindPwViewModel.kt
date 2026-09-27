package com.example.on_safe.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.VerifyResetIdentityRequest
import com.example.on_safe.network.isOk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class FindPwUiState(
    val isLoading: Boolean = false,
    val isVerifyEnabled: Boolean = true
)

// 본인확인을 통과한 아이디와 서버가 발급한 재설정 티켓 — 재설정 화면으로 함께 넘긴다
data class ResetTarget(
    val userId: String,
    val resetTicket: String
)

class FindPwViewModel : ViewModel() {

    private val _uiState = MutableLiveData(FindPwUiState())
    val uiState: LiveData<FindPwUiState> = _uiState

    // 한 번 보여주면 소비되는 토스트 메시지
    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    // 재설정 화면 이동 1회성 신호 — Activity가 소비 후 onNavigated()로 리셋
    // 확인에 성공한 아이디를 그대로 넘긴다 — 화면에서 다시 읽으면 응답을 기다리는 사이
    // 사용자가 입력칸을 고친 경우 다른 계정으로 넘어간다
    private val _navigateToReset = MutableLiveData<ResetTarget?>(null)
    val navigateToReset: LiveData<ResetTarget?> = _navigateToReset

    // 아이디+이름+이메일 대조 → 일치하면 재설정 티켓 발급
    fun verifyIdentity(userId: String, name: String, email: String) {
        setState { copy(isVerifyEnabled = false, isLoading = true) }
        viewModelScope.launch {
            try {
                val response = ApiClient.api.verifyResetIdentity(
                    VerifyResetIdentityRequest(userId = userId, name = name, mail = email)
                )
                val ticket = response.body()?.data?.resetTicket
                if (response.isOk && !ticket.isNullOrBlank()) {
                    _navigateToReset.value = ResetTarget(userId, ticket)
                } else {
                    // 어느 항목이 틀렸는지 구분하지 않는다 — 아이디 존재 여부가 드러나지 않게
                    _toastMessage.value = "입력하신 정보와 일치하는 계정이 없습니다."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _toastMessage.value = "네트워크 오류가 발생했습니다."
            } finally {
                setState { copy(isVerifyEnabled = true, isLoading = false) }
            }
        }
    }

    fun onToastShown() {
        _toastMessage.value = null
    }

    fun onNavigated() {
        _navigateToReset.value = null
    }

    private inline fun setState(update: FindPwUiState.() -> FindPwUiState) {
        _uiState.value = (_uiState.value ?: FindPwUiState()).update()
    }
}
