package com.example.on_safe.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.CheckIdRequest
import com.example.on_safe.network.dto.CheckMailRequest
import com.example.on_safe.network.dto.RegisterRequest
import com.example.on_safe.network.dto.SendEmailCodeRequest
import com.example.on_safe.network.dto.VerifyEmailCodeRequest
import com.example.on_safe.network.errorMessage
import com.example.on_safe.network.isOk
import com.example.on_safe.util.EmailValidator
import com.example.on_safe.util.FieldValidation
import com.example.on_safe.util.PasswordValidator
import com.example.on_safe.util.PhoneField
import com.example.on_safe.util.VerificationCodeTimer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Step1 동의 화면의 체크값 묶음 — 서버가 동의 이력을 남기므로 실제 값을 그대로 전달한다
data class ConsentChoices(
    val terms: Boolean,
    val privacy: Boolean,
    val sensitive: Boolean,
    val marketing: Boolean
)

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
    // verifyEmailCode 성공 시 서버가 발급한 1회용 티켓. register 요청 시 함께 전송해야 통과된다.
    // 이메일 재인증(같은 mail 로 verifyEmailCode 를 다시 성공)하면 새 티켓으로 덮어씀.
    val emailVerifyTicket: String? = null,

    // 이름 / 주소 (별도 유효성 없이 비어있는지만 확인)
    val isNameFilled: Boolean = false,
    val isAddressFilled: Boolean = false,

    val isLoading: Boolean = false,
    val isCompleteEnabled: Boolean = false
)

class RegisterStep2ViewModel : ViewModel() {

    private val _uiState = MutableLiveData(RegisterStep2UiState())
    val uiState: LiveData<RegisterStep2UiState> = _uiState

    private val state: RegisterStep2UiState
        get() = _uiState.value ?: RegisterStep2UiState()

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

    // 상호검증·완료 버튼 판단에 필요한 원본 텍스트 캐시
    private var idText = ""
    private var pwText = ""
    private var pwConfirmText = ""
    private var nameText = ""
    private var addressText = ""
    private var email = ""

    // 이메일 인증 요청 — 주소가 바뀌면 취소해 이전 주소의 응답이 새 주소에 적용되지 않게 한다
    private var emailJob: Job? = null

    // 인증번호 확인 요청 — 발송/재발송과 서로 취소하지 않도록 따로 둔다
    private var verifyJob: Job? = null

    // 아이디 중복확인 요청 — 같은 이유로 아이디가 바뀌면 취소한다
    private var idJob: Job? = null

    // ── 아이디 ──
    fun onIdChanged(id: String) {
        idText = id
        // 아이디 변경 시 이전 중복확인 결과 무효화
        idJob?.cancel()
        setState { copy(isIdCheckEnabled = true, isIdChecked = false, idValidation = FieldValidation.Empty) }
        recomputeComplete()
    }

    // TODO: [백엔드] userId 길이·문자셋 서버 검증 부재 — 앱 외 클라이언트로 임의 아이디 등록 가능.
    // TODO: [백엔드] 전화번호를 정규화 없이 저장·비교해 하이픈 유무로 중복 검사 우회 가능.
    fun checkId(id: String) {
        if (!ID_REGEX.matches(id)) {
            setState { copy(idValidation = FieldValidation.Invalid("영문/숫자 6~12자로 입력해주세요.")) }
            return
        }
        setState { copy(isIdCheckEnabled = false) }
        // 응답 대기 중 아이디가 바뀌면 뒤늦은 성공이 무효화된 확인 결과를 되살린다
        idJob?.cancel()
        idJob = viewModelScope.launch {
            try {
                val response = ApiClient.api.checkId(CheckIdRequest(userId = id))
                if (response.isOk) {
                    setState {
                        copy(
                            isIdChecked = true,
                            idValidation = FieldValidation.Valid("✓ 사용 가능한 아이디입니다.")
                        )
                    }
                } else {
                    val msg = response.errorMessage("이미 사용 중인 아이디입니다.")
                    setState { copy(idValidation = FieldValidation.Invalid(msg), isIdCheckEnabled = true) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _toastMessage.value = "네트워크 오류가 발생했습니다."
                setState { copy(isIdCheckEnabled = true) }
            }
            recomputeComplete()
        }
    }

    // ── 비밀번호 / 비밀번호 확인 ──
    fun onPwChanged(pw: String) {
        // 전송할 때 trim하므로 검증도 같은 값으로 한다. 원본으로 검증하면
        // 끝에 공백이 붙은 8자가 앱은 통과하고 서버 @Size(min=8)에서 거부된다.
        pwText = pw.trim()
        val validation = when {
            pwText.isEmpty() -> FieldValidation.Empty
            PasswordValidator.isValid(pwText) -> FieldValidation.Valid(PasswordValidator.SUCCESS_MSG)
            else -> FieldValidation.Invalid(PasswordValidator.ERROR_MSG)
        }
        setState { copy(pwValidation = validation) }
        // 비번 변경 시 기존 비번확인 재검증
        recomputePwConfirm()
        recomputeComplete()
    }

    fun onPwConfirmChanged(confirm: String) {
        pwConfirmText = confirm.trim()
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

    // ── 전화번호 (Activity가 하이픈 포맷을 마친 최종 문자열 전달) ──
    fun onPhoneChanged(formattedPhone: String) {
        val validation = when {
            formattedPhone.isEmpty() -> FieldValidation.Empty
            PhoneField.isValid(formattedPhone) -> FieldValidation.Valid(PhoneField.SUCCESS_MSG)
            else -> FieldValidation.Invalid(PhoneField.ERROR_MSG)
        }
        setState { copy(phoneValidation = validation) }
        recomputeComplete()
    }

    // ── 이름 ──
    fun onNameChanged(name: String) {
        nameText = name
        setState { copy(isNameFilled = name.isNotBlank()) }
        recomputeComplete()
    }

    // ── 주소 (주소 검색 결과 복귀 시에만 호출) ──
    fun onAddressChanged(address: String) {
        addressText = address
        setState { copy(isAddressFilled = address.isNotEmpty()) }
        recomputeComplete()
    }

    // ── 이메일 ──
    fun onEmailChanged(newEmail: String) {
        email = newEmail
        // 이메일 변경 시 인증 상태·타이머 초기화 — 뒤늦은 타이머 콜백 차단
        emailTimer.cancel()
        // 주소가 바뀌면 진행 중이던 요청은 의미가 없다. 아래 setState가 버튼 상태도 함께 되돌린다.
        emailJob?.cancel()
        verifyJob?.cancel()
        val validation = when {
            newEmail.isEmpty() -> FieldValidation.Empty
            EmailValidator.isValid(newEmail) -> FieldValidation.Valid(EmailValidator.SUCCESS_MSG)
            else -> FieldValidation.Invalid(EmailValidator.ERROR_MSG)
        }
        setState {
            copy(
                emailValidation = validation,
                isEmailVerified = false,
                isEmailCodeLayoutVisible = false,
                isEmailVerifyEnabled = true,
                // 주소가 바뀌면 기존 티켓도 무효 — register 시점에 이 티켓이 남아 있으면
                // 서버가 mail 대조에서 거부하지만 프론트에서도 미리 정리해 UI 상태 일관성 유지.
                emailVerifyTicket = null,
            )
        }
        recomputeComplete()
    }

    fun verifyEmail() {
        if (state.emailValidation !is FieldValidation.Valid) {
            _toastMessage.value = "올바른 이메일을 입력해주세요."
            return
        }
        setState { copy(isEmailVerifyEnabled = false) }
        // 요청 시점의 주소로 고정 — 응답을 기다리는 사이 사용자가 주소를 바꿔도
        // 중복확인을 통과한 주소로만 코드가 나가게 한다
        val target = email
        emailJob?.cancel()
        // 인증 메일 발송 전에 서버에 중복 여부 조회 — SES 비용/스팸 방지 및
        // 이미 가입된 이메일이면 즉시 사용자에게 알림.
        emailJob = viewModelScope.launch {
            try {
                val response = ApiClient.api.checkMail(CheckMailRequest(mail = target))
                if (response.isOk) {
                    sendEmailCode(target, isResend = false)
                } else {
                    val msg = response.errorMessage("이미 사용 중인 이메일입니다.")
                    setState {
                        copy(
                            emailValidation = FieldValidation.Invalid(msg),
                            isEmailVerifyEnabled = true
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _toastMessage.value = "네트워크 오류가 발생했습니다."
                setState { copy(isEmailVerifyEnabled = true) }
            }
        }
    }

    fun resendEmailCode() {
        // 진행 중인 인증 요청은 취소하지 않는다 — 취소 경로에는 인증 버튼 복구가 없어
        // 재전송까지 실패하면 버튼이 비활성으로 고착된다.
        if (emailJob?.isActive == true) return
        setState { copy(isEmailResendVisible = false) }
        val target = email
        emailJob = viewModelScope.launch { sendEmailCode(target, isResend = true) }
    }

    // 발송·재발송 공통 — 안내 문구와 실패 시 복구 대상만 다름.
    // 호출부의 코루틴 안에서 이어 실행돼 주소 변경 시 함께 취소된다.
    private suspend fun sendEmailCode(target: String, isResend: Boolean) {
        try {
            val response = ApiClient.api.sendEmailCode(SendEmailCodeRequest(mail = target))
            if (response.isOk) {
                startEmailVerification(isResend)
            } else {
                restoreSendButton(isResend)
                _toastMessage.value = response.errorMessage(
                    if (isResend) "인증 메일 재발송에 실패했습니다." else "인증 메일 발송에 실패했습니다."
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            restoreSendButton(isResend)
            _toastMessage.value = "네트워크 오류가 발생했습니다."
        }
    }

    private fun restoreSendButton(isResend: Boolean) {
        if (isResend) setState { copy(isEmailResendVisible = true) }
        else setState { copy(isEmailVerifyEnabled = true) }
    }

    private fun startEmailVerification(isResend: Boolean) {
        setState {
            copy(
                isEmailVerifyEnabled = false,
                isEmailCodeLayoutVisible = true,
                isEmailResendVisible = true,
                isConfirmCodeEnabled = true
            )
        }
        _toastMessage.value = if (isResend) "인증 메일을 재발송했습니다." else "인증 메일을 발송했습니다."
        emailTimer.start()
    }

    fun confirmEmailCode(code: String) {
        setState { copy(isConfirmCodeEnabled = false) }
        // 주소가 바뀌면 취소한다 — 뒤늦게 도착한 성공이 isEmailVerified를 되살려
        // 인증하지 않은 새 주소로 가입이 나갈 수 있다.
        // 발송/재발송(emailJob)과 분리한다. 같이 묶으면 확인 요청이 진행 중인 재발송을
        // 취소하고, 취소 경로엔 재전송 링크 복구가 없어 링크가 영구히 사라진다.
        val target = email
        verifyJob?.cancel()
        verifyJob = viewModelScope.launch {
            try {
                val response = ApiClient.api.verifyEmailCode(VerifyEmailCodeRequest(mail = target, code = code))
                if (response.isOk) {
                    emailTimer.cancel()
                    // 서버가 발급한 1회용 티켓을 저장 — register 시 함께 실어 인증 소유권 증명.
                    // 서버 응답 data 가 (뭔가 이유로) 없으면 서버 스펙 불일치 상황이라 인증 실패 처리.
                    val ticket = response.body()?.data?.emailVerifyTicket
                    if (ticket.isNullOrBlank()) {
                        _toastMessage.value = "인증 확인에 실패했습니다. 다시 시도해주세요."
                        setState { copy(isConfirmCodeEnabled = true) }
                        recomputeComplete()
                        return@launch
                    }
                    // 인증 완료 후에도 "재전송"이 남아 있던 문제 — 함께 숨김
                    setState {
                        copy(
                            isEmailVerified = true,
                            isEmailCodeLayoutVisible = false,
                            isEmailResendVisible = false,
                            emailVerifyTicket = ticket,
                        )
                    }
                } else {
                    _toastMessage.value = response.errorMessage("인증코드가 올바르지 않습니다.")
                    setState { copy(isConfirmCodeEnabled = true) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _toastMessage.value = "네트워크 오류가 발생했습니다."
                setState { copy(isConfirmCodeEnabled = true) }
            }
            recomputeComplete()
        }
    }

    // ── 최종 회원가입 ──
    // marketingConsent: Step1 화면의 마케팅 정보 수신 체크박스 값을 그대로 서버에 반영
    // TODO: [백엔드] 만 14세 미만 가입 제한 도입 시 생년월일 입력란과 birthDate 필드 추가 필요.
    fun register(password: String, phone: String, addressDetail: String, consents: ConsentChoices) {
        // 티켓이 없다면 이메일 인증이 미완료 상태 — completeReady 판정에서 이미 걸러지지만
        // 방어적으로 한 번 더 확인해 부분 검증 상태로 서버까지 보내지 않게 한다.
        val ticket = state.emailVerifyTicket
        if (ticket.isNullOrBlank()) {
            _toastMessage.value = "이메일 인증을 먼저 완료해주세요."
            return
        }
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
                        termsAgreed = consents.terms,
                        privacyPolicyAgreed = consents.privacy,
                        sensitiveInfoAgreed = consents.sensitive,
                        marketingConsent = consents.marketing,
                        emailVerifyTicket = ticket
                    )
                )
                if (response.isOk) {
                    _toastMessage.value = "회원가입이 완료되었습니다. 로그인해주세요."
                    _registerSuccess.value = true
                } else {
                    _toastMessage.value = response.errorMessage("회원가입에 실패했습니다.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _toastMessage.value = "네트워크 오류가 발생했습니다."
            } finally {
                setState { copy(isLoading = false) }
            }
        }
    }

    // 완료 버튼 비활성 사유 안내 — 중복확인·이메일 인증은 화면에 흔적이 남지 않아
    // 원인 파악이 어려움. 위에서부터 첫 미충족 항목 하나만 표시.
    fun showFirstMissingRequirement() {
        firstMissingRequirement(state)?.let { _toastMessage.value = it }
    }

    // 완료 버튼 판정과 안내 문구의 기준을 하나로 유지 — 미충족 항목 없음 = 가입 가능
    // (etAddressDetail은 선택 항목 → 필수 조건 제외)
    private fun firstMissingRequirement(s: RegisterStep2UiState): String? = when {
        idText.isEmpty() -> "아이디를 입력해주세요."
        !s.isIdChecked -> "아이디 중복확인을 해주세요."
        s.pwValidation !is FieldValidation.Valid -> "비밀번호 형식을 확인해주세요."
        s.pwConfirmValidation !is FieldValidation.Valid -> "비밀번호가 일치하지 않습니다."
        !s.isNameFilled -> "이름을 입력해주세요."
        s.phoneValidation !is FieldValidation.Valid -> "전화번호 형식을 확인해주세요."
        s.emailValidation !is FieldValidation.Valid -> "이메일 형식을 확인해주세요."
        !s.isEmailVerified -> "이메일 인증을 완료해주세요."
        !s.isAddressFilled -> "주소를 입력해주세요."
        else -> null
    }

    private fun recomputeComplete() {
        setState { copy(isCompleteEnabled = firstMissingRequirement(this) == null) }
    }

    fun onToastShown() {
        _toastMessage.value = null
    }

    fun onRegisterHandled() {
        _registerSuccess.value = false
    }

    private inline fun setState(update: RegisterStep2UiState.() -> RegisterStep2UiState) {
        _uiState.value = (_uiState.value ?: RegisterStep2UiState()).update()
    }

    override fun onCleared() {
        super.onCleared()
        emailTimer.cancel()
    }

    private companion object {
        // 매 입력마다 재생성되지 않도록 상수화
        val ID_REGEX = Regex("^[A-Za-z0-9]{6,12}$")
    }
}
