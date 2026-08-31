package com.example.on_safe.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.on_safe.FieldValidation
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.CheckIdRequest
import com.example.on_safe.network.dto.RegisterRequest
import com.example.on_safe.network.dto.SendEmailCodeRequest
import com.example.on_safe.network.dto.VerifyEmailCodeRequest
import com.example.on_safe.util.PasswordValidator
import com.example.on_safe.util.VerificationCodeTimer
import kotlinx.coroutines.launch

data class RegisterStep2UiState(
    // 아이디
    val isIdCheckEnabled: Boolean = true,
    val idValidation: FieldValidation = FieldValidation.Empty,
    val isIdChecked: Boolean = false,

    // 비밀번호 / 비밀번호 확인
    val pwValidation: FieldValidation = FieldValidation.Empty,
    val pwConfirmValidation: FieldValidation = FieldValidation.Empty,

    // 전화번호
    val phoneValidation: FieldValidation = FieldValidation.Empty,

    // 이메일 + 인증
    val emailValidation: FieldValidation = FieldValidation.Empty,
    val isEmailVerifyEnabled: Boolean = true,
    val isEmailCodeLayoutVisible: Boolean = false,
    val isEmailVerified: Boolean = false,
    val isConfirmCodeEnabled: Boolean = false,
    val isEmailResendVisible: Boolean = false,
    val emailTimerText: String = "",

    // 이름 / 주소 (별도 유효성 없이 비어있는지만 확인)
    val isNameFilled: Boolean = false,
    val isAddressFilled: Boolean = false,

    val isLoading: Boolean = false,
    val isCompleteEnabled: Boolean = false
)

class RegisterStep2ViewModel : ViewModel() {

    private val _uiState = MutableLiveData(RegisterStep2UiState())
    val uiState: LiveData<RegisterStep2UiState> = _uiState

    // 한 번 보여주면 소비되는 토스트 메시지
    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    // 회원가입 성공 시 로그인 화면으로 넘어가라는 1회성 신호
    private val _registerSuccess = MutableLiveData(false)
    val registerSuccess: LiveData<Boolean> = _registerSuccess

    private val emailTimer = VerificationCodeTimer(
        onTick = { text -> setState { copy(emailTimerText = text) } },
        onFinish = {
            setState {
                copy(
                    emailTimerText = "0:00",
                    isConfirmCodeEnabled = false,
                    isEmailVerifyEnabled = true
                )
            }
        }
    )

    // 상호검증(비번↔비번확인)이나 최종 완료 버튼 판단에 필요해서 캐싱해두는 원본 텍스트
    private var idText = ""
    private var pwText = ""
    private var pwConfirmText = ""
    private var nameText = ""
    private var addressText = ""
    private var email = ""

    // ── 아이디 ──
    fun onIdChanged(id: String) {
        idText = id
        // 아이디를 바꾸면 이전 중복확인 결과는 무효화
        setState { copy(isIdCheckEnabled = true, isIdChecked = false, idValidation = FieldValidation.Empty) }
        recomputeComplete()
    }

    fun checkId(id: String) {
        val idRegex = Regex("^[A-Za-z0-9]{6,12}$")
        if (!idRegex.matches(id)) {
            setState { copy(idValidation = FieldValidation.Invalid("영문/숫자 6~12자로 입력해주세요.")) }
            return
        }
        setState { copy(isIdCheckEnabled = false) }
        viewModelScope.launch {
            try {
                val response = ApiClient.api.checkId(CheckIdRequest(userId = id))
                if (response.isSuccessful && response.body()?.success == true) {
                    setState {
                        copy(
                            isIdChecked = true,
                            idValidation = FieldValidation.Valid("✓ 사용 가능한 아이디입니다.")
                        )
                    }
                } else {
                    val msg = response.body()?.message ?: ApiClient.parseErrorMessage(response.errorBody(), "이미 사용 중인 아이디입니다.")
                    setState { copy(idValidation = FieldValidation.Invalid(msg), isIdCheckEnabled = true) }
                }
            } catch (e: Exception) {
                _toastMessage.value = "네트워크 오류가 발생했습니다."
                setState { copy(isIdCheckEnabled = true) }
            }
            recomputeComplete()
        }
    }

    // ── 비밀번호 / 비밀번호 확인 ──
    fun onPwChanged(pw: String) {
        pwText = pw
        val validation = when {
            pw.isEmpty() -> FieldValidation.Empty
            PasswordValidator.isValid(pw) -> FieldValidation.Valid(PasswordValidator.SUCCESS_MSG)
            else -> FieldValidation.Invalid(PasswordValidator.ERROR_MSG)
        }
        setState { copy(pwValidation = validation) }
        // 비번이 바뀌면 이미 입력해둔 비번확인도 다시 검증
        recomputePwConfirm()
        recomputeComplete()
    }

    fun onPwConfirmChanged(confirm: String) {
        pwConfirmText = confirm
        recomputePwConfirm()
        recomputeComplete()
    }

    private fun recomputePwConfirm() {
        val validation = when {
            pwConfirmText.isEmpty() -> FieldValidation.Empty
            pwConfirmText == pwText -> FieldValidation.Valid(PasswordValidator.MATCH_MSG)
            else -> FieldValidation.Invalid(PasswordValidator.MISMATCH_MSG)
        }
        setState { copy(pwConfirmValidation = validation) }
    }

    // ── 전화번호 (Activity에서 자동 하이픈 포맷까지 끝낸 최종 문자열을 넘겨받음) ──
    fun onPhoneChanged(formattedPhone: String) {
        val validation = when {
            formattedPhone.isEmpty() -> FieldValidation.Empty
            // 010은 항상 11자리(가운데 4자리), 구번호(011/016~019)만 3~4자리를 허용한다
            Regex("^(010-\\d{4}|01[16789]-\\d{3,4})-\\d{4}$").matches(formattedPhone) ->
                FieldValidation.Valid("✓ 올바른 전화번호입니다.")
            else -> FieldValidation.Invalid("010-0000-0000 형식으로 입력해주세요.")
        }
        setState { copy(phoneValidation = validation) }
        recomputeComplete()
    }

    // ── 이름 ──
    fun onNameChanged(name: String) {
        nameText = name
        setState { copy(isNameFilled = name.isNotEmpty()) }
        recomputeComplete()
    }

    // ── 주소 (주소 검색 화면에서 결과로 돌아왔을 때만 호출됨) ──
    fun onAddressChanged(address: String) {
        addressText = address
        setState { copy(isAddressFilled = address.isNotEmpty()) }
        recomputeComplete()
    }

    // ── 이메일 ──
    fun onEmailChanged(newEmail: String) {
        email = newEmail
        // 이메일을 바꾸면 인증 상태·타이머 전부 초기화 — 켜져 있던 타이머가 뒤늦게 화면을 건드리지 않도록 취소
        emailTimer.cancel()
        val validation = when {
            newEmail.isEmpty() -> FieldValidation.Empty
            Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$").matches(newEmail) ->
                FieldValidation.Valid("✓ 올바른 이메일 형식입니다.")
            else -> FieldValidation.Invalid("올바른 이메일 형식을 입력해주세요.")
        }
        setState {
            copy(
                emailValidation = validation,
                isEmailVerified = false,
                isEmailCodeLayoutVisible = false,
                isEmailVerifyEnabled = true
            )
        }
        recomputeComplete()
    }

    fun verifyEmail() {
        val validEmail = (_uiState.value ?: RegisterStep2UiState()).emailValidation is FieldValidation.Valid
        if (!validEmail) {
            _toastMessage.value = "올바른 이메일을 입력해주세요."
            return
        }
        setState { copy(isEmailVerifyEnabled = false) }
        viewModelScope.launch {
            try {
                val response = ApiClient.api.sendEmailCode(SendEmailCodeRequest(mail = email))
                if (response.isSuccessful && response.body()?.success == true) {
                    startEmailVerification()
                } else {
                    _toastMessage.value = response.body()?.message ?: ApiClient.parseErrorMessage(response.errorBody(), "인증 메일 발송에 실패했습니다.")
                    setState { copy(isEmailVerifyEnabled = true) }
                }
            } catch (e: Exception) {
                _toastMessage.value = "네트워크 오류가 발생했습니다."
                setState { copy(isEmailVerifyEnabled = true) }
            }
        }
    }

    fun confirmEmailCode(code: String) {
        setState { copy(isConfirmCodeEnabled = false) }
        viewModelScope.launch {
            try {
                val response = ApiClient.api.verifyEmailCode(VerifyEmailCodeRequest(mail = email, code = code))
                if (response.isSuccessful && response.body()?.success == true) {
                    emailTimer.cancel()
                    setState {
                        copy(
                            isEmailVerified = true,
                            isEmailCodeLayoutVisible = false
                        )
                    }
                } else {
                    _toastMessage.value = response.body()?.message ?: ApiClient.parseErrorMessage(response.errorBody(), "인증코드가 올바르지 않습니다.")
                    setState { copy(isConfirmCodeEnabled = true) }
                }
            } catch (e: Exception) {
                _toastMessage.value = "네트워크 오류가 발생했습니다."
                setState { copy(isConfirmCodeEnabled = true) }
            }
            recomputeComplete()
        }
    }

    fun resendEmailCode() {
        setState { copy(isEmailResendVisible = false) }
        viewModelScope.launch {
            try {
                val response = ApiClient.api.sendEmailCode(SendEmailCodeRequest(mail = email))
                if (response.isSuccessful && response.body()?.success == true) {
                    startEmailVerification()
                    _toastMessage.value = "인증 메일을 재발송했습니다."
                } else {
                    setState { copy(isEmailResendVisible = true) }
                    _toastMessage.value = response.body()?.message ?: ApiClient.parseErrorMessage(response.errorBody(), "인증 메일 재발송에 실패했습니다.")
                }
            } catch (e: Exception) {
                setState { copy(isEmailResendVisible = true) }
                _toastMessage.value = "네트워크 오류가 발생했습니다."
            }
        }
    }

    private fun startEmailVerification() {
        setState {
            copy(
                isEmailVerifyEnabled = false,
                isEmailCodeLayoutVisible = true,
                isEmailResendVisible = true,
                isConfirmCodeEnabled = true
            )
        }
        _toastMessage.value = "인증 메일을 발송했습니다."
        emailTimer.start()
    }

    // ── 최종 회원가입 ──
    // marketingConsent: Step1 화면의 마케팅 정보 수신 체크박스 값을 그대로 전달받아 서버에 반영
    fun register(password: String, phone: String, addressDetail: String, marketingConsent: Boolean) {
        setState { copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val response = ApiClient.api.register(
                    RegisterRequest(
                        userId = idText.trim(),
                        password = password.trim(),
                        name = nameText.trim(),
                        mail = email.trim(),
                        phone = phone.trim(),
                        address = addressText.trim().ifEmpty { null },
                        addressDetail = addressDetail.trim().ifEmpty { null },
                        marketingConsent = marketingConsent
                    )
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    _toastMessage.value = "회원가입이 완료되었습니다. 로그인해주세요."
                    _registerSuccess.value = true
                } else {
                    _toastMessage.value = response.body()?.message ?: ApiClient.parseErrorMessage(response.errorBody(), "회원가입에 실패했습니다.")
                }
            } catch (e: Exception) {
                _toastMessage.value = "네트워크 오류가 발생했습니다."
            } finally {
                setState { copy(isLoading = false) }
            }
        }
    }

    // 완료 버튼이 왜 안 눌리는지 알려준다 — 중복확인·이메일 인증은 화면에 표시가 남지 않아
    // 사용자가 원인을 찾기 어렵다. 위에서부터 첫 번째 미충족 항목 하나만 안내한다.
    fun showFirstMissingRequirement() {
        val state = _uiState.value ?: RegisterStep2UiState()
        val message = when {
            idText.isEmpty() -> "아이디를 입력해주세요."
            !state.isIdChecked -> "아이디 중복확인을 해주세요."
            state.pwValidation !is FieldValidation.Valid -> "비밀번호 형식을 확인해주세요."
            state.pwConfirmValidation !is FieldValidation.Valid -> "비밀번호가 일치하지 않습니다."
            !state.isNameFilled -> "이름을 입력해주세요."
            state.phoneValidation !is FieldValidation.Valid -> "전화번호 형식을 확인해주세요."
            state.emailValidation !is FieldValidation.Valid -> "이메일 형식을 확인해주세요."
            !state.isEmailVerified -> "이메일 인증을 완료해주세요."
            !state.isAddressFilled -> "주소를 입력해주세요."
            else -> null
        }
        if (message != null) _toastMessage.value = message
    }

    fun onToastShown() {
        _toastMessage.value = null
    }

    fun onRegisterHandled() {
        _registerSuccess.value = false
    }

    private fun recomputeComplete() {
        val state = _uiState.value ?: RegisterStep2UiState()
        // etAddressDetail은 RegisterRequest에서 nullable 선택 항목 → 필수 조건 제외
        val allValid = idText.isNotEmpty() &&
                state.isIdChecked &&
                state.pwValidation is FieldValidation.Valid &&
                state.pwConfirmValidation is FieldValidation.Valid &&
                state.isNameFilled &&
                state.phoneValidation is FieldValidation.Valid &&
                state.emailValidation is FieldValidation.Valid &&
                state.isEmailVerified &&
                state.isAddressFilled
        setState { copy(isCompleteEnabled = allValid) }
    }

    private inline fun setState(update: RegisterStep2UiState.() -> RegisterStep2UiState) {
        _uiState.value = (_uiState.value ?: RegisterStep2UiState()).update()
    }

    override fun onCleared() {
        super.onCleared()
        emailTimer.cancel()
    }
}
