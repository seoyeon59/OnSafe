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
import kotlinx.coroutines.launch

// 비밀번호 확인 결과 — 성공 시 폼을 바로 채울 수 있도록 최신 사용자 정보를 함께 담아 보낸다
data class VerifyResult(
    val success: Boolean,
    val message: String? = null,
    val user: UserResponse? = null
)

data class SaveResult(val success: Boolean, val message: String)

class EditProfileViewModel : ViewModel() {

    private val _verifyResult = MutableLiveData<VerifyResult?>()
    val verifyResult: LiveData<VerifyResult?> = _verifyResult

    private val _saveResult = MutableLiveData<SaveResult?>()
    val saveResult: LiveData<SaveResult?> = _saveResult

    // 서버에서 조회한 마케팅 동의 현재값 — 실패 시 null로 남겨 Activity가 로컬 캐시 값을 그대로 유지하게 한다
    private val _marketingConsent = MutableLiveData<Boolean?>()
    val marketingConsent: LiveData<Boolean?> = _marketingConsent

    fun verifyPassword(userId: String, password: String) {
        viewModelScope.launch {
            try {
                val verifyResponse = ApiClient.api.verifyPassword(userId, VerifyPasswordRequest(password))
                val verifyBody = verifyResponse.body()
                if (!verifyResponse.isSuccessful || verifyBody?.success != true) {
                    _verifyResult.value = VerifyResult(
                        success = false,
                        message = ApiClient.parseErrorMessage(verifyResponse.errorBody(), "비밀번호가 올바르지 않습니다.")
                    )
                    return@launch
                }

                // 검증 성공 → 곧바로 최신 사용자 정보를 받아와 폼에 채울 수 있게 함께 전달
                val userResponse = ApiClient.api.getUser(userId)
                val userBody = userResponse.body()
                if (userResponse.isSuccessful && userBody?.success == true && userBody.data != null) {
                    _verifyResult.value = VerifyResult(success = true, user = userBody.data)
                } else {
                    // 비밀번호 검증은 통과했지만 정보 조회만 실패한 경우 — 폼은 비운 채로라도 열어준다
                    _verifyResult.value = VerifyResult(success = true, user = null)
                }
            } catch (e: Exception) {
                _verifyResult.value = VerifyResult(success = false, message = "네트워크 오류가 발생했습니다.")
            }
        }
    }

    // 서버에서 받아온 원본 — 변경 여부 판단에 쓴다(바뀐 게 없으면 서버 호출 자체를 하지 않음)
    private var original: UserResponse? = null

    fun onUserLoaded(user: UserResponse) {
        original = user
    }

    // 입력값이 원본과 하나라도 다른지
    // 전화번호는 하이픈 유무가 서버 저장값과 다를 수 있어 숫자만 뽑아 비교한다
    fun hasChanges(
        name: String,
        phone: String,
        email: String,
        address: String,
        addressDetail: String
    ): Boolean {
        val o = original ?: return true   // 원본을 못 받았으면 그냥 저장 시도
        return name != o.name ||
                phone.digitsOnly() != o.phone.digitsOnly() ||
                email != o.mail ||
                address != o.address.orEmpty() ||
                addressDetail != o.addressDetail.orEmpty()
    }

    private fun String.digitsOnly() = filter { it.isDigit() }

    fun save(
        userId: String,
        name: String,
        phone: String,
        email: String,
        address: String,
        addressDetail: String
    ) {
        viewModelScope.launch {
            try {
                val response = ApiClient.api.updateUser(
                    userId,
                    UserUpdateRequest(
                        name = name,
                        mail = email,
                        phone = phone,
                        address = address,
                        addressDetail = addressDetail
                    )
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    _saveResult.value = SaveResult(true, "정보가 저장되었습니다.")
                } else {
                    _saveResult.value = SaveResult(
                        false,
                        ApiClient.parseErrorMessage(response.errorBody(), "저장에 실패했습니다.")
                    )
                }
            } catch (e: Exception) {
                _saveResult.value = SaveResult(false, "네트워크 오류로 저장에 실패했습니다.")
            }
        }
    }

    // 비밀번호 확인 성공 직후 호출 — 서버의 최신 마케팅 동의 상태로 스위치를 맞춘다
    fun loadMarketingConsent(userId: String) {
        viewModelScope.launch {
            try {
                val response = ApiClient.api.getMarketingConsent(userId)
                val body = response.body()
                if (response.isSuccessful && body?.success == true && body.data != null) {
                    _marketingConsent.value = body.data.consent
                }
                // 실패 시 아무 것도 내보내지 않음 — Activity가 로컬 캐시로 표시한 값을 그대로 유지
            } catch (_: Exception) {
                // 조회 실패 → 캐시 값 유지
            }
        }
    }

    // 스위치 조작 시 서버에 반영 (실패해도 로컬 표시는 유지 — 다음 조회 때 서버 값으로 재동기화)
    fun updateMarketingConsent(userId: String, consent: Boolean) {
        viewModelScope.launch {
            try {
                ApiClient.api.updateMarketingConsent(userId, MarketingConsentRequest(consent))
            } catch (_: Exception) {
                // 무시 — 다음 조회 때 서버 값으로 재동기화
            }
        }
    }

    fun onVerifyResultHandled() {
        _verifyResult.value = null
    }

    fun onSaveResultHandled() {
        _saveResult.value = null
    }
}
