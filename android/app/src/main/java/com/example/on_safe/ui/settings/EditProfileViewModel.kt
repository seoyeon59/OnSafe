package com.example.on_safe.ui.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.MarketingConsentRequest
import com.example.on_safe.network.dto.UserResponse
import com.example.on_safe.network.dto.UserUpdateRequest
import com.example.on_safe.network.dto.VerifyPasswordRequest
import com.example.on_safe.network.failure
import com.example.on_safe.network.isOk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// 비밀번호 확인 결과 — 성공 시 폼을 바로 채우도록 최신 사용자 정보 동봉
// isReauth: 저장 중 티켓 만료로 다시 확인한 경우 — 폼 유지 후 저장 재시도
data class VerifyResult(
    val success: Boolean,
    val message: String? = null,
    val user: UserResponse? = null,
    val isReauth: Boolean = false
)

// reauthRequired: 재인증 티켓 만료(10분)·누락 — 비밀번호 재확인 필요
data class SaveResult(val success: Boolean, val message: String, val reauthRequired: Boolean = false)

class EditProfileViewModel : ViewModel() {

    private val _verifyResult = MutableLiveData<VerifyResult?>()
    val verifyResult: LiveData<VerifyResult?> = _verifyResult

    private val _saveResult = MutableLiveData<SaveResult?>()
    val saveResult: LiveData<SaveResult?> = _saveResult

    // 저장 진행 중 여부 — 버튼 비활성화와 연타로 인한 중복 PUT 방지에 함께 사용
    private val _isSaving = MutableLiveData(false)
    val isSaving: LiveData<Boolean> = _isSaving

    // 서버의 마케팅 동의 현재값 — 실패 시 null 유지로 Activity의 로컬 캐시 값 보존
    private val _marketingConsent = MutableLiveData<Boolean?>()
    val marketingConsent: LiveData<Boolean?> = _marketingConsent

    // 마케팅 동의 저장 실패 — 되돌릴 이전 값. 법적 동의 이력이라 화면·서버 불일치 방지
    private val _marketingRevert = MutableLiveData<Boolean?>()
    val marketingRevert: LiveData<Boolean?> = _marketingRevert

    // verifyPassword 응답 재인증 티켓 — 저장 요청에 첨부, 10분·저장 성공 시 소비
    private var reauthTicket: String? = null

    fun verifyPassword(userId: String, password: String, isReauth: Boolean = false) {
        viewModelScope.launch {
            try {
                val verifyResponse = ApiClient.api.verifyPassword(userId, VerifyPasswordRequest(password))
                val ticket = verifyResponse.body()?.data?.reauthTicket
                if (!verifyResponse.isOk || ticket == null) {
                    _verifyResult.value = VerifyResult(
                        success = false,
                        message = verifyResponse.failure("비밀번호가 올바르지 않습니다.").message,
                        isReauth = isReauth
                    )
                    return@launch
                }
                reauthTicket = ticket
                // 재확인은 티켓 갱신만 — 폼을 서버 값으로 덮으면 입력 중인 값 유실
                if (isReauth) {
                    _verifyResult.value = VerifyResult(success = true, isReauth = true)
                    return@launch
                }

                // 검증 성공 → 최신 사용자 정보를 받아 폼 채우기용으로 동봉
                val userResponse = ApiClient.api.getUser(userId)
                val userBody = userResponse.body()
                if (userResponse.isSuccessful && userBody?.success == true && userBody.data != null) {
                    _verifyResult.value = VerifyResult(success = true, user = userBody.data)
                } else {
                    // 검증은 통과했으나 정보 조회만 실패 — 폼은 비운 채로 개방
                    _verifyResult.value = VerifyResult(success = true, user = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _verifyResult.value = VerifyResult(
                    success = false, message = "네트워크 오류가 발생했습니다.", isReauth = isReauth
                )
            }
        }
    }

    // 서버 원본 — 변경 여부 판단용 (미변경 시 서버 호출 생략)
    private var original: UserResponse? = null

    fun onUserLoaded(user: UserResponse) {
        original = user
    }

    // 입력값과 원본의 차이 여부 — 전화번호는 하이픈 유무 차이를 무시하고 숫자만 비교.
    // 이메일은 변경 불가(인증 UI 미구현)라 비교 제외
    fun hasChanges(
        name: String,
        phone: String,
        address: String,
        addressDetail: String
    ): Boolean {
        val o = original ?: return true   // 원본 미수신 시 저장 시도
        return name != o.name ||
                phone.digitsOnly() != o.phone.digitsOnly() ||
                address != o.address.orEmpty() ||
                addressDetail != o.addressDetail.orEmpty()
    }

    private fun String.digitsOnly() = filter { it.isDigit() }

    // 메일은 보내지 않음 — 변경에 메일 인증 티켓이 필요하고, 화면에서 변경 불가
    fun save(
        userId: String,
        name: String,
        phone: String,
        address: String,
        addressDetail: String
    ) {
        if (_isSaving.value == true) return
        _isSaving.value = true
        viewModelScope.launch {
            try {
                val response = ApiClient.api.updateUser(
                    userId,
                    UserUpdateRequest(
                        name = name,
                        phone = phone,
                        address = address,
                        addressDetail = addressDetail,
                        reauthTicket = reauthTicket
                    )
                )
                if (response.isOk) {
                    reauthTicket = null   // 성공 시 서버에서 소비
                    _saveResult.value = SaveResult(true, "정보가 저장되었습니다.")
                } else {
                    val failure = response.failure("저장에 실패했습니다.")
                    _saveResult.value = if (failure.code == "REAUTH_REQUIRED") {
                        SaveResult(false, "확인 시간이 지났습니다. 비밀번호를 다시 입력해주세요.", reauthRequired = true)
                    } else {
                        SaveResult(false, failure.message)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _saveResult.value = SaveResult(false, "네트워크 오류로 저장에 실패했습니다.")
            } finally {
                _isSaving.value = false
            }
        }
    }

    // 비밀번호 확인 직후 호출 — 서버의 최신 마케팅 동의 상태로 스위치 동기화
    fun loadMarketingConsent(userId: String) {
        viewModelScope.launch {
            try {
                val response = ApiClient.api.getMarketingConsent(userId)
                val body = response.body()
                if (response.isSuccessful && body?.success == true && body.data != null) {
                    _marketingConsent.value = body.data.consent
                }
                // 실패 시 미방출 — Activity의 로컬 캐시 표시 유지
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 조회 실패 → 캐시 값 유지
            }
        }
    }

    // 스위치 조작 시 서버 반영 — 실패 시 이전 값으로 되돌림 신호
    fun updateMarketingConsent(userId: String, consent: Boolean) {
        viewModelScope.launch {
            val ok = try {
                ApiClient.api.updateMarketingConsent(userId, MarketingConsentRequest(consent)).isOk
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (!ok) _marketingRevert.value = !consent
        }
    }

    fun onMarketingRevertHandled() {
        _marketingRevert.value = null
    }

    fun onVerifyResultHandled() {
        _verifyResult.value = null
    }

    fun onSaveResultHandled() {
        _saveResult.value = null
    }
}
