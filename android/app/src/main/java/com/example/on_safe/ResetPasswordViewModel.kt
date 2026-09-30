package com.example.on_safe

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.ResetPasswordRequest
import com.example.on_safe.network.dto.UserUpdateRequest
import com.example.on_safe.network.errorMessage
import com.example.on_safe.network.failure
import com.example.on_safe.network.isOk
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

    // 저장 후 화면 처리 1회성 신호
    enum class SaveOutcome {
        CLOSE,                 // 비밀번호 찾기 경유 성공 — 화면 닫기
        RELOGIN,               // 설정 경유 성공 — 서버가 모든 세션을 끊어 재로그인 필요
        RESTART_VERIFICATION   // 재설정 티켓 만료·사용됨 — 비밀번호 찾기부터 다시
    }

    private val _saveOutcome = MutableLiveData<SaveOutcome?>(null)
    val saveOutcome: LiveData<SaveOutcome?> = _saveOutcome

    private var mode = ResetPasswordActivity.MODE_FIND_PW
    private var userId = ""
    private var resetTicket = ""   // MODE_FIND_PW 전용 — verifyResetCode 응답 1회용 티켓

    private var newPw = ""
    private var newPwConfirm = ""
    private var currentPw = ""   // MODE_SETTINGS에서만 사용 — 서버 본인확인에 전송

    // Intent의 모드·유저아이디·티켓을 onCreate에서 1회 전달받음
    fun init(mode: String, userId: String, resetTicket: String = "") {
        this.mode = mode
        this.userId = userId
        this.resetTicket = resetTicket
    }

    // 전송 직전이 아니라 입력 시점에 trim한다 — 회원가입·로그인이 모두 trim한 값을 쓰므로
    // 여기서만 원본을 보내면 끝에 공백이 붙은 비밀번호로 바뀌어 로그인이 영영 안 된다.
    fun onNewPasswordChanged(pw: String) {
        newPw = pw.trim()
        recompute()
    }

    fun onConfirmChanged(confirm: String) {
        newPwConfirm = confirm.trim()
        recompute()
    }

    fun onCurrentPasswordChanged(password: String) {
        currentPw = password.trim()
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

    // 진입 경로별 엔드포인트 상이 — reset-password는 본인확인 티켓 필수
    fun save() {
        // 아이디가 없으면 요청 경로가 깨져 "현재 비밀번호가 올바르지 않습니다"로 잘못 안내된다
        if (userId.isBlank()) {
            _toastMessage.value = "로그인 정보가 없습니다. 다시 로그인해주세요."
            return
        }
        // 티켓 없이 보내면 서버가 거부한다 — 원인을 알 수 있게 미리 안내
        if (mode == ResetPasswordActivity.MODE_FIND_PW && resetTicket.isBlank()) {
            _toastMessage.value = "본인확인 정보가 없습니다. 비밀번호 찾기를 다시 진행해주세요."
            return
        }
        setState { copy(isLoading = true) }
        viewModelScope.launch {
            try {
                if (mode == ResetPasswordActivity.MODE_SETTINGS) {
                    saveFromSettings()
                } else {
                    saveAfterIdentityCheck()
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
        if (response.isOk) {
            // 서버가 이 기기 포함 모든 세션 종료 — 다음 요청의 "세션 만료" 오해 방지
            _saveOutcome.value = SaveOutcome.RELOGIN
        } else {
            _toastMessage.value = response.errorMessage("현재 비밀번호가 올바르지 않습니다.")
        }
    }

    // 비밀번호 찾기 경유 — 본인확인(아이디+이름+이메일)이 이미 끝난 상태
    private suspend fun saveAfterIdentityCheck() {
        val response = ApiClient.api.resetPassword(
            ResetPasswordRequest(userId = userId, resetTicket = resetTicket, newPassword = newPw)
        )
        if (response.isOk) {
            _toastMessage.value = "비밀번호가 변경되었습니다."
            _saveOutcome.value = SaveOutcome.CLOSE
            return
        }
        // 서버 원문을 그대로 쓰지 않는다 — 영문 검증 메시지가 섞여 오면 걸러진다
        val failure = response.failure("비밀번호 변경에 실패했습니다.")
        if (failure.code == "INVALID_RESET_CODE") {
            _toastMessage.value = "인증 시간이 지났습니다. 다시 인증해주세요."
            _saveOutcome.value = SaveOutcome.RESTART_VERIFICATION
        } else {
            // 서버 원문을 그대로 쓰지 않는다 — 영문 검증 메시지가 섞여 오면 걸러진다
            // 티켓 만료로 실패할 수 있으니 다시 시도하도록 안내
            _toastMessage.value = response.errorMessage("비밀번호 변경에 실패했습니다. 비밀번호 찾기를 다시 진행해주세요.")
        }
    }

    fun onToastShown() {
        _toastMessage.value = null
    }

    fun onSaveHandled() {
        _saveOutcome.value = null
    }

    private inline fun setState(update: ResetPasswordUiState.() -> ResetPasswordUiState) {
        _uiState.value = (_uiState.value ?: ResetPasswordUiState()).update()
    }
}
