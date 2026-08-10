package com.example.on_safe

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.ResetPasswordRequest
import com.example.on_safe.util.PasswordValidator
import kotlinx.coroutines.launch

// 입력칸 하나의 검증 결과 — 어떤 메시지/색을 보여줄지는 뷰모델이 판단하고, Activity는 그대로 표시만 함
sealed class FieldValidation {
    object Empty : FieldValidation()
    data class Valid(val message: String) : FieldValidation()
    data class Invalid(val message: String) : FieldValidation()
}

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
    private var isCurrentPwFilled = false

    // Intent로 넘어온 모드/유저아이디를 Activity의 onCreate에서 한 번만 넘겨받음
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

    fun onCurrentPasswordChanged(filled: Boolean) {
        isCurrentPwFilled = filled
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
        // MODE_FIND_PW(비밀번호 찾기로 진입)에서는 현재 비밀번호 입력칸 자체가 없으므로 조건에서 제외
        val saveEnabled = isNewPwValid && isConfirmValid &&
                (mode == ResetPasswordActivity.MODE_FIND_PW || isCurrentPwFilled)

        _uiState.value = (_uiState.value ?: ResetPasswordUiState()).copy(
            newPwValidation = newPwValidation,
            confirmValidation = confirmValidation,
            isSaveEnabled = saveEnabled
        )
    }

    fun save() {
        _uiState.value = (_uiState.value ?: ResetPasswordUiState()).copy(isLoading = true)
        viewModelScope.launch {
            try {
                // TODO: 백엔드 currentPassword 필드 추가 시 현재 비밀번호 값도 함께 전송
                val response = ApiClient.api.resetPassword(
                    ResetPasswordRequest(userId = userId, newPassword = newPw)
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    _toastMessage.value = "비밀번호가 변경되었습니다."
                    _saveSuccess.value = true
                } else {
                    _toastMessage.value = response.body()?.message ?: "비밀번호 변경에 실패했습니다."
                }
            } catch (e: Exception) {
                _toastMessage.value = "네트워크 오류가 발생했습니다."
            } finally {
                _uiState.value = (_uiState.value ?: ResetPasswordUiState()).copy(isLoading = false)
            }
        }
    }

    fun onToastShown() {
        _toastMessage.value = null
    }

    fun onSaveHandled() {
        _saveSuccess.value = false
    }
}
