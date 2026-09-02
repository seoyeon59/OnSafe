package com.example.on_safe.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.FindIdRequest
import com.example.on_safe.network.dto.SendEmailCodeRequest
import com.example.on_safe.network.dto.VerifyEmailCodeRequest
import com.example.on_safe.network.errorMessage
import com.example.on_safe.network.isOk
import com.example.on_safe.util.VerificationCodeTimer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// 아이디 찾기 화면 상태 — Activity는 값을 받아 화면 반영만
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

    // 1회성 토스트 — onToastShown()으로 소비, 화면 회전 시 재출력 방지
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

    // 인증코드 발송 — name은 Activity 검증용일 뿐 요청에 불필요해 파라미터 제외
    fun requestCode(email: String) {
        setState { copy(isRequestCodeEnabled = false, isLoading = true) }
        sendCode(email, isResend = false)
    }

    fun resendCode(email: String) {
        setState { copy(isResendVisible = false) }
        sendCode(email, isResend = true)
    }

    // 발송·재발송 공통 — 안내 문구와 실패 시 복구 대상만 다름
    private fun sendCode(email: String, isResend: Boolean) {
        viewModelScope.launch {
            try {
                val response = ApiClient.api.sendEmailCode(SendEmailCodeRequest(mail = email))
                if (response.isOk) {
                    startVerification(isResend)
                } else {
                    restoreSendButton(isResend)
                    _toastMessage.value = response.errorMessage(
                        if (isResend) "인증번호 재발송에 실패했습니다." else "인증 메일 발송에 실패했습니다."
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
        _toastMessage.value = if (isResend) "인증번호를 재발송했습니다." else "인증번호를 발송했습니다."
        timer.start()
    }

    // 인증코드 확인 → 아이디 조회
    fun confirmCode(code: String, email: String, name: String) {
        setState { copy(isConfirmEnabled = false, isLoading = true) }
        viewModelScope.launch {
            try {
                val verifyResponse = ApiClient.api.verifyEmailCode(VerifyEmailCodeRequest(mail = email, code = code))
                if (!verifyResponse.isOk) {
                    _toastMessage.value = verifyResponse.errorMessage("인증코드가 올바르지 않습니다.")
                    setState { copy(isConfirmEnabled = true) }
                    return@launch
                }

                val findResponse = ApiClient.api.findId(FindIdRequest(name = name, mail = email))
                val foundId = findResponse.body()?.data?.userId
                if (findResponse.isOk && foundId != null) {
                    timer.cancel()
                    // 결과 표시 후에도 "재전송"이 남아 있던 문제 — 함께 숨김
                    setState {
                        copy(
                            isResultVisible = true,
                            foundId = foundId,
                            isCodeLayoutVisible = false,
                            isResendVisible = false,
                            isConfirmEnabled = true
                        )
                    }
                } else {
                    _toastMessage.value = findResponse.errorMessage("아이디를 찾을 수 없습니다.")
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

    private inline fun setState(update: FindIdUiState.() -> FindIdUiState) {
        _uiState.value = (_uiState.value ?: FindIdUiState()).update()
    }

    // 뷰모델 소멸 시 타이머 정리
    override fun onCleared() {
        super.onCleared()
        timer.cancel()
    }
}
