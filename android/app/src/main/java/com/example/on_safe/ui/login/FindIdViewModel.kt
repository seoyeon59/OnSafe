package com.example.on_safe.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.FindIdRequest
import com.example.on_safe.network.dto.SendEmailCodeRequest
import com.example.on_safe.network.dto.VerifyEmailCodeRequest
import com.example.on_safe.util.VerificationCodeTimer
import kotlinx.coroutines.launch

// 아이디 찾기 화면 상태를 한 덩어리로 표현 — Activity는 이 값을 받아서 화면에 반영만 함
data class FindIdUiState(
    val isLoading: Boolean = false,
    val isRequestCodeEnabled: Boolean = true,
    val isCodeLayoutVisible: Boolean = false,
    val isResendVisible: Boolean = false,
    val isConfirmEnabled: Boolean = false,
    val timerText: String = "",
    val isResultVisible: Boolean = false,
    val foundId: String = ""
)

class FindIdViewModel : ViewModel() {

    private val _uiState = MutableLiveData(FindIdUiState())
    val uiState: LiveData<FindIdUiState> = _uiState

    // 한 번 보여주면 소비되는 토스트 메시지 — 보여준 뒤 onToastShown()으로 비워서 화면 회전 시 재출력 방지
    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

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

    // 인증코드 발송 — name은 검증만 Activity에서 하고 이 요청 자체엔 필요 없어서 파라미터에서 제외
    fun requestCode(email: String) {
        setState { copy(isRequestCodeEnabled = false, isLoading = true) }
        viewModelScope.launch {
            try {
                val response = ApiClient.api.sendEmailCode(SendEmailCodeRequest(mail = email))
                if (response.isSuccessful && response.body()?.success == true) {
                    startVerification()
                } else {
                    _toastMessage.value = response.body()?.message ?: ApiClient.parseErrorMessage(response.errorBody(), "인증 메일 발송에 실패했습니다.")
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

    // 인증코드 확인 → 아이디 조회
    fun confirmCode(code: String, email: String, name: String) {
        setState { copy(isConfirmEnabled = false, isLoading = true) }
        viewModelScope.launch {
            try {
                val verifyResponse = ApiClient.api.verifyEmailCode(VerifyEmailCodeRequest(mail = email, code = code))
                if (verifyResponse.isSuccessful && verifyResponse.body()?.success == true) {
                    val findResponse = ApiClient.api.findId(FindIdRequest(name = name, mail = email))
                    val findBody = findResponse.body()
                    if (findResponse.isSuccessful && findBody?.success == true && findBody.data != null) {
                        timer.cancel()
                        setState {
                            copy(
                                isResultVisible = true,
                                foundId = findBody.data.userId,
                                isCodeLayoutVisible = false,
                                isConfirmEnabled = true
                            )
                        }
                    } else {
                        _toastMessage.value = findBody?.message
                            ?: ApiClient.parseErrorMessage(findResponse.errorBody(), "아이디를 찾을 수 없습니다.")
                        setState { copy(isConfirmEnabled = true) }
                    }
                } else {
                    _toastMessage.value = verifyResponse.body()?.message ?: ApiClient.parseErrorMessage(verifyResponse.errorBody(), "인증코드가 올바르지 않습니다.")
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
    fun resendCode(email: String) {
        setState { copy(isResendVisible = false) }
        viewModelScope.launch {
            try {
                val response = ApiClient.api.sendEmailCode(SendEmailCodeRequest(mail = email))
                if (response.isSuccessful && response.body()?.success == true) {
                    startVerification()
                    _toastMessage.value = "인증번호를 재발송했습니다."
                } else {
                    setState { copy(isResendVisible = true) }
                    _toastMessage.value = response.body()?.message ?: ApiClient.parseErrorMessage(response.errorBody(), "인증번호 재발송에 실패했습니다.")
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

    private fun startVerification() {
        setState {
            copy(
                isRequestCodeEnabled = false,
                isCodeLayoutVisible = true,
                isResendVisible = true,
                isConfirmEnabled = true
            )
        }
        _toastMessage.value = "인증번호를 발송했습니다."
        timer.start()
    }

    private inline fun setState(update: FindIdUiState.() -> FindIdUiState) {
        _uiState.value = (_uiState.value ?: FindIdUiState()).update()
    }

    // 화면(뷰모델)이 완전히 사라질 때 타이머 정리 — Activity의 onDestroy에서 하던 것을 그대로 옮김
    override fun onCleared() {
        super.onCleared()
        timer.cancel()
    }
}
