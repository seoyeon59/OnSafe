package com.example.on_safe.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.SendResetCodeRequest
import com.example.on_safe.network.dto.VerifyResetCodeRequest
import com.example.on_safe.network.errorMessage
import com.example.on_safe.network.isOk
import com.example.on_safe.util.VerificationCodeTimer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// FindIdUiState와 동일 구조 — 결과 카드 대신 비밀번호 재설정 화면으로 이동
data class FindPwUiState(
    val isLoading: Boolean = false,
    val isRequestCodeEnabled: Boolean = true,
    val isCodeLayoutVisible: Boolean = false,
    val isResendVisible: Boolean = false,
    val isConfirmEnabled: Boolean = false,
    val timerText: String = ""
)

class FindPwViewModel : ViewModel() {

    private val _uiState = MutableLiveData(FindPwUiState())
    val uiState: LiveData<FindPwUiState> = _uiState

    // 한 번 보여주면 소비되는 토스트 메시지
    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    // 재설정 화면 이동 1회성 신호 — Activity가 소비 후 onNavigated()로 리셋
    private val _navigateToReset = MutableLiveData(false)
    val navigateToReset: LiveData<Boolean> = _navigateToReset

    private val timer = VerificationCodeTimer(
        onTick = { text -> setState { copy(timerText = text) } },
        onFinish = {
            setState {
                copy(
                    timerText = "0:00",
                    isConfirmEnabled = false,
                    isResendVisible = true,
                    isRequestCodeEnabled = true
                )
            }
        }
    )

    fun requestCode(userId: String, email: String) {
        setState { copy(isRequestCodeEnabled = false, isLoading = true) }
        sendCode(userId, email, isResend = false)
    }

    fun resendCode(userId: String, email: String) {
        setState { copy(isResendVisible = false) }
        sendCode(userId, email, isResend = true)
    }

    // 발송·재발송 공통 — 안내 문구와 실패 시 복구 대상만 다름
    private fun sendCode(userId: String, email: String, isResend: Boolean) {
        viewModelScope.launch {
            try {
                val response = ApiClient.api.sendResetCode(SendResetCodeRequest(userId = userId, mail = email))
                if (response.isOk) {
                    startVerification(isResend)
                } else {
                    restoreSendButton(isResend)
                    _toastMessage.value = response.errorMessage(
                        if (isResend) "재설정 코드 재발송에 실패했습니다." else "코드 발송에 실패했습니다."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                restoreSendButton(isResend)
                _toastMessage.value = "네트워크 오류가 발생했습니다."
            } finally {
                // 재발송은 애초에 로딩을 켜지 않음 — 진행 중인 다른 요청의 스피너를 끄지 않도록 제외
                if (!isResend) setState { copy(isLoading = false) }
            }
        }
    }

    private fun restoreSendButton(isResend: Boolean) {
        if (isResend) setState { copy(isResendVisible = true) }
        else setState { copy(isRequestCodeEnabled = true) }
    }

    private fun startVerification(isResend: Boolean) {
        setState {
            copy(
                isRequestCodeEnabled = false,
                isCodeLayoutVisible = true,
                isResendVisible = true,
                isConfirmEnabled = true
            )
        }
        _toastMessage.value = if (isResend) "재설정 코드를 재발송했습니다." else "재설정 코드를 발송했습니다."
        timer.start()
    }

    // 재설정 코드 확인
    fun confirmCode(userId: String, code: String) {
        setState { copy(isConfirmEnabled = false, isLoading = true) }
        viewModelScope.launch {
            try {
                val response = ApiClient.api.verifyResetCode(VerifyResetCodeRequest(userId = userId, code = code))
                if (response.isOk) {
                    timer.cancel()
                    _navigateToReset.value = true
                } else {
                    _toastMessage.value = response.errorMessage("코드가 올바르지 않습니다.")
                    setState { copy(isConfirmEnabled = true) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _toastMessage.value = "네트워크 오류가 발생했습니다."
                setState { copy(isConfirmEnabled = true) }
            } finally {
                setState { copy(isLoading = false) }
            }
        }
    }

    fun onToastShown() {
        _toastMessage.value = null
    }

    fun onNavigated() {
        _navigateToReset.value = false
    }

    private inline fun setState(update: FindPwUiState.() -> FindPwUiState) {
        _uiState.value = (_uiState.value ?: FindPwUiState()).update()
    }

    override fun onCleared() {
        super.onCleared()
        timer.cancel()
    }
}
