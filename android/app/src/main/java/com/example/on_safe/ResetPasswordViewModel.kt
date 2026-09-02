package com.example.on_safe

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.ResetPasswordRequest
import com.example.on_safe.network.dto.UserUpdateRequest
import com.example.on_safe.util.FieldValidation
import com.example.on_safe.util.PasswordValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class ResetPasswordUiState(
    val isLoading: Boolean = false,
    val newPwValidation: FieldValidation = FieldValidation.Empty,
    val confirmValidation: FieldValidation = FieldValidation.Empty,
    val isSaveEnabled: Boolean = false
)

class ResetPasswordViewModel : ViewModel() {

    private val _uiState = MutableLiveData(ResetPasswordUiState())
    val uiState: LiveData<ResetPasswordUiState> = _uiState

    // 한 번 보여주면 소비되는 토스트 메시지
    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    // 저장 성공 시 화면을 닫으라는 1회성 신호
    private val _saveSuccess = MutableLiveData(false)
    val saveSuccess: LiveData<Boolean> = _saveSuccess

    private var mode = ResetPasswordActivity.MODE_FIND_PW
    private var userId = ""

    private var newPw = ""
    private var newPwConfirm = ""
    private var currentPw = ""   // MODE_SETTINGS에서만 사용 — 서버 본인확인에 전송

    // Intent의 모드·유저아이디를 onCreate에서 1회 전달받음
    fun init(mode: String, userId: String) {
        this.mode = mode
        this.userId = userId
    }

    fun onNewPasswordChanged(pw: String) {
        newPw = pw
        recompute()
    }

    fun onConfirmChanged(confirm: String) {
        newPwConfirm = confirm
        recompute()
    }

    fun onCurrentPasswordChanged(password: String) {
        currentPw = password
        recompute()
    }

    private fun recompute() {
        val newPwValidation = when {
            newPw.isEmpty() -> FieldValidation.Empty
            PasswordValidator.isValid(newPw) -> FieldValidation.Valid(PasswordValidator.SUCCESS_MSG)
            else -> FieldValidation.Invalid(PasswordValidator.ERROR_MSG)
        }
        val confirmValidation = when {
            newPwConfirm.isEmpty() -> FieldValidation.Empty
            newPwConfirm == newPw -> FieldValidation.Valid(PasswordValidator.MATCH_MSG)
            else -> FieldValidation.Invalid(PasswordValidator.MISMATCH_MSG)
        }
        val isNewPwValid = newPwValidation is FieldValidation.Valid
        val isConfirmValid = confirmValidation is FieldValidation.Valid
        // MODE_FIND_PW는 현재 비밀번호 입력칸 부재 — 조건에서 제외
        val saveEnabled = isNewPwValid && isConfirmValid &&
                (mode == ResetPasswordActivity.MODE_FIND_PW || currentPw.isNotEmpty())

        setState {
            copy(
                newPwValidation = newPwValidation,
                confirmValidation = confirmValidation,
                isSaveEnabled = saveEnabled
            )
        }
    }

    // 진입 경로별 엔드포인트 상이 — reset-password는 이메일 코드 인증 선행 필수
    fun save() {
        setState { copy(isLoading = true) }
        viewModelScope.launch {
            try {
                if (mode == ResetPasswordActivity.MODE_SETTINGS) {
                    saveFromSettings()
                } else {
                    saveAfterCodeVerification()
                }
            } catch (e: CancellationException) {
                throw e   // 화면 종료로 인한 취소 — "네트워크 오류" 오표시 방지
            } catch (_: Exception) {
                _toastMessage.value = "네트워크 오류가 발생했습니다."
            } finally {
                setState { copy(isLoading = false) }
            }
        }
    }

    // 설정 경유 — 서버 본인확인용 currentPassword 동봉 필수
    private suspend fun saveFromSettings() {
        val response = ApiClient.api.updateUser(
            userId,
            UserUpdateRequest(currentPassword = currentPw, password = newPw)
        )
        if (response.isSuccessful && response.body()?.success == true) {
            onSaveSucceeded()
        } else {
            _toastMessage.value = ApiClient.parseErrorMessage(
                response.errorBody(), "현재 비밀번호가 올바르지 않습니다."
            )
        }
    }

    // 비밀번호 찾기 경유 — 이메일 코드 인증이 이미 끝난 상태
    private suspend fun saveAfterCodeVerification() {
        val response = ApiClient.api.resetPassword(
            ResetPasswordRequest(userId = userId, newPassword = newPw)
        )
        if (response.isSuccessful && response.body()?.success == true) {
            onSaveSucceeded()
        } else {
            _toastMessage.value = response.body()?.message
                ?: ApiClient.parseErrorMessage(response.errorBody(), "비밀번호 변경에 실패했습니다.")
        }
    }

    // 두 경로 공통 성공 처리
    private fun onSaveSucceeded() {
        _toastMessage.value = "비밀번호가 변경되었습니다."
        _saveSuccess.value = true
    }

    fun onToastShown() {
        _toastMessage.value = null
    }

    fun onSaveHandled() {
        _saveSuccess.value = false
    }

    private inline fun setState(update: ResetPasswordUiState.() -> ResetPasswordUiState) {
        _uiState.value = (_uiState.value ?: ResetPasswordUiState()).update()
    }
}
