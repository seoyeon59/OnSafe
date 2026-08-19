package com.example.on_safe.ui.login

import android.os.CountDownTimer
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.SendResetCodeRequest
import com.example.on_safe.network.dto.VerifyResetCodeRequest
import kotlinx.coroutines.launch

// FindIdUiState와 구조는 같지만 이 화면엔 "결과 카드"가 없고 대신 다음 화면(비밀번호 재설정)으로 넘어감
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

    // 코드 확인 성공 시 비밀번호 재설정 화면으로 넘어가라는 1회성 신호 — Activity에서 소비 후 onNavigated()로 리셋
    private val _navigateToReset = MutableLiveData(false)
    val navigateToReset: LiveData<Boolean> = _navigateToReset

    private var countDownTimer: CountDownTimer? = null

    // 재설정 코드 발송
    fun requestCode(userId: String, email: String) {
        setState { copy(isRequestCodeEnabled = false, isLoading = true) }
        viewModelScope.launch {
            try {
                val response = ApiClient.api.sendResetCode(SendResetCodeRequest(userId = userId, mail = email))
                if (response.isSuccessful && response.body()?.success == true) {
                    startVerification()
                } else {
                    _toastMessage.value = response.body()?.message ?: "코드 발송에 실패했습니다."
                    setState { copy(isRequestCodeEnabled = true) }
                }
            } catch (e: Exception) {
                _toastMessage.value = "네트워크 오류가 발생했습니다."
                setState { copy(isRequestCodeEnabled = true) }
            } finally {
                setState { copy(isLoading = false) }
            }
        }
    }

    // 재설정 코드 확인
    fun confirmCode(userId: String, code: String) {
        setState { copy(isConfirmEnabled = false, isLoading = true) }
        viewModelScope.launch {
            try {
                val response = ApiClient.api.verifyResetCode(VerifyResetCodeRequest(userId = userId, code = code))
                if (response.isSuccessful && response.body()?.success == true) {
                    countDownTimer?.cancel()
                    _navigateToReset.value = true
                } else {
                    _toastMessage.value = response.body()?.message ?: "코드가 올바르지 않습니다."
                    setState { copy(isConfirmEnabled = true) }
                }
            } catch (e: Exception) {
                _toastMessage.value = "네트워크 오류가 발생했습니다."
                setState { copy(isConfirmEnabled = true) }
            } finally {
                setState { copy(isLoading = false) }
            }
        }
    }

    // 재전송
    fun resendCode(userId: String, email: String) {
        setState { copy(isResendVisible = false) }
        viewModelScope.launch {
            try {
                val response = ApiClient.api.sendResetCode(SendResetCodeRequest(userId = userId, mail = email))
                if (response.isSuccessful && response.body()?.success == true) {
                    startVerification()
                    _toastMessage.value = "재설정 코드를 재발송했습니다."
                } else {
                    setState { copy(isResendVisible = true) }
                    _toastMessage.value = response.body()?.message ?: "재설정 코드 재발송에 실패했습니다."
                }
            } catch (e: Exception) {
                setState { copy(isResendVisible = true) }
                _toastMessage.value = "네트워크 오류가 발생했습니다."
            }
        }
    }

    fun onToastShown() {
        _toastMessage.value = null
    }

    fun onNavigated() {
        _navigateToReset.value = false
    }

    private fun startVerification() {
        setState {
            copy(
                isRequestCodeEnabled = false,
                isCodeLayoutVisible = true,
                isResendVisible = true,
                isConfirmEnabled = true
            )
        }
        _toastMessage.value = "재설정 코드를 발송했습니다."
        startTimer()
    }

    private fun startTimer() {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(180_000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 60000
                val seconds = (millisUntilFinished % 60000) / 1000
                setState { copy(timerText = String.format("%d:%02d", minutes, seconds)) }
            }

            override fun onFinish() {
                setState {
                    copy(
                        timerText = "0:00",
                        isConfirmEnabled = false,
                        isResendVisible = true,
                        isRequestCodeEnabled = true
                    )
                }
            }
        }.start()
    }

    private inline fun setState(update: FindPwUiState.() -> FindPwUiState) {
        _uiState.value = (_uiState.value ?: FindPwUiState()).update()
    }

    override fun onCleared() {
        super.onCleared()
        countDownTimer?.cancel()
    }
}
