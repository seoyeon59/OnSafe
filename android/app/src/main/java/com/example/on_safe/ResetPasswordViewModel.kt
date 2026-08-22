package com.example.on_safe

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.ResetPasswordRequest
import com.example.on_safe.network.dto.UserUpdateRequest
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
    // MODE_SETTINGS에서만 사용 — 입력 여부만이 아니라 실제 값이 필요하다(서버 본인확인에 전송).
    private var currentPw = ""

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
        // MODE_FIND_PW(비밀번호 찾기로 진입)에서는 현재 비밀번호 입력칸 자체가 없으므로 조건에서 제외
        val saveEnabled = isNewPwValid && isConfirmValid &&
                (mode == ResetPasswordActivity.MODE_FIND_PW || currentPw.isNotEmpty())

        _uiState.value = (_uiState.value ?: ResetPasswordUiState()).copy(
            newPwValidation = newPwValidation,
            confirmValidation = confirmValidation,
            isSaveEnabled = saveEnabled
        )
    }

    // 진입 경로에 따라 서버 엔드포인트가 다르다.
    //
    // MODE_FIND_PW(비밀번호 찾기 → 이메일 코드 인증 완료 후 진입):
    //   POST /api/auth/reset-password. 이 엔드포인트는 서버가 2단계(verify-reset-code) 완료 플래그
    //   (Redis reset_verified:{userId}, TTL 10분)를 검증하므로, 코드 인증을 거친 이 경로에서만 성공한다.
    //
    // MODE_SETTINGS(설정 → 비밀번호 변경):
    //   이메일 코드 인증을 거치지 않아 위 플래그가 없으므로 reset-password를 호출하면 400이 된다.
    //   대신 PUT /api/users/{userId}를 쓴다. 이 엔드포인트는 password를 보내면 currentPassword를
    //   함께 요구하고 서버가 직접 대조하므로(UserService.updateUser), 별도 본인확인 호출 없이
    //   한 번의 요청으로 "현재 비밀번호 검증 + 새 비밀번호 반영"이 모두 처리된다.
    //   불일치 시 INVALID_PASSWORD 에러가 내려온다.
    fun save() {
        _uiState.value = (_uiState.value ?: ResetPasswordUiState()).copy(isLoading = true)
        viewModelScope.launch {
            try {
                if (mode == ResetPasswordActivity.MODE_SETTINGS) {
                    saveFromSettings()
                } else {
                    saveAfterCodeVerification()
                }
            } catch (e: Exception) {
                _toastMessage.value = "네트워크 오류가 발생했습니다."
            } finally {
                _uiState.value = (_uiState.value ?: ResetPasswordUiState()).copy(isLoading = false)
            }
        }
    }

    // 설정 경유 — currentPassword를 함께 보내야 서버가 본인확인 후 변경해준다.
    // (currentPassword를 빠뜨리면 서버가 INVALID_PASSWORD로 거부한다)
    private suspend fun saveFromSettings() {
        val response = ApiClient.api.updateUser(
            userId,
            UserUpdateRequest(currentPassword = currentPw, password = newPw)
        )
        if (response.isSuccessful && response.body()?.success == true) {
            _toastMessage.value = "비밀번호가 변경되었습니다."
            _saveSuccess.value = true
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
            _toastMessage.value = "비밀번호가 변경되었습니다."
            _saveSuccess.value = true
        } else {
            _toastMessage.value = response.body()?.message
                ?: ApiClient.parseErrorMessage(response.errorBody(), "비밀번호 변경에 실패했습니다.")
        }
    }

    fun onToastShown() {
        _toastMessage.value = null
    }

    fun onSaveHandled() {
        _saveSuccess.value = false
    }
}
