package com.example.on_safe.ui.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.NotificationSettingsRequest
import com.example.on_safe.network.dto.NotificationSettingsResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// 저장 실패 등 1회성 토스트 메시지
data class SettingsToastEvent(val message: String)

// 로그아웃/회원탈퇴 결과 — Activity가 토큰 정리 + 화면 이동을 담당하므로 성공 여부와 메시지만 전달
data class AuthResultEvent(val success: Boolean, val message: String)

// SharedPreferences 캐시 읽기/쓰기와 스위치·아이콘 등 View 조작은 Context/View에 강하게 묶여 있어
// 그대로 Activity에 남기고, 서버 통신과 그 결과(성공/실패) 판단만 이쪽으로 옮겼다.
//
// 생성자에 기본값 파라미터를 두지 않는다 — by viewModels()의 기본 팩토리는 리플렉션으로
// "진짜 무인자 생성자"를 찾는데, Kotlin은 기본값 파라미터를 별도의 무인자 생성자로 노출하지
// 않아 런타임에 NoSuchMethodException이 난다. 그래서 필드 초기값으로 대신 지정한다.
class SettingsViewModel : ViewModel() {

    private val _userName = MutableLiveData<String>()
    val userName: LiveData<String> = _userName

    // 서버에서 새로 받아온 알림 설정 — Activity가 받아서 캐시·스위치·아이콘에 반영
    private val _serverSettings = MutableLiveData<NotificationSettingsResponse?>()
    val serverSettings: LiveData<NotificationSettingsResponse?> = _serverSettings

    private val _toastEvent = MutableLiveData<SettingsToastEvent?>()
    val toastEvent: LiveData<SettingsToastEvent?> = _toastEvent

    // 1회성 이벤트 — true면 로그아웃 처리 완료, Activity가 소비 후 다시 null로
    private val _logoutEvent = MutableLiveData<Boolean?>()
    val logoutEvent: LiveData<Boolean?> = _logoutEvent

    private val _withdrawResult = MutableLiveData<AuthResultEvent?>()
    val withdrawResult: LiveData<AuthResultEvent?> = _withdrawResult

    // 빠르게 연속으로 토글할 때 같은 항목의 이전 PUT이 늦게 도착해 최신 상태를 덮어쓰지 않도록,
    // 항목(알림/소리/진동)별로 별도 Job을 두고 같은 항목이 다시 바뀔 때만 이전 요청을 취소한다.
    private var notificationUpdateJob: Job? = null
    private var soundUpdateJob: Job? = null
    private var vibrationUpdateJob: Job? = null

    fun loadUserName(userId: String) {
        if (userId.isBlank()) {
            // 로그인 정보가 없을 때(사실상 도달하지 않는 방어 경로)의 기본 표시값
            _userName.value = "${DEFAULT_GUARDIAN_LABEL}님"
            return
        }
        viewModelScope.launch {
            try {
                val response = ApiClient.api.getUser(userId)
                val body = response.body()
                _userName.value = if (response.isSuccessful && body?.success == true && body.data != null) {
                    "${body.data.name} 보호자님"
                } else {
                    "보호자님"
                }
            } catch (_: Exception) {
                _userName.value = "보호자님"
            }
        }
    }

    // 서버 최신 값 조회. 실패 시 값을 내보내지 않아 Activity가 캐시로 표시한 값을 그대로 유지하게 한다.
    fun loadNotificationSettings(userId: String) {
        if (userId.isBlank()) return
        viewModelScope.launch {
            try {
                val response = ApiClient.api.getNotificationSettings(userId)
                val body = response.body()
                if (response.isSuccessful && body?.success == true && body.data != null) {
                    _serverSettings.value = body.data
                }
            } catch (_: Exception) {
                // 조회 실패 → 아무 것도 내보내지 않음
            }
        }
    }

    // 사용자 조작 시 서버 PUT (부분 업데이트)
    fun updateNotificationSetting(
        userId: String,
        notification: Boolean? = null,
        sound: Boolean? = null,
        vibration: Boolean? = null
    ) {
        if (userId.isBlank()) return

        val newJob = viewModelScope.launch {
            try {
                val response = ApiClient.api.updateNotificationSettings(
                    userId,
                    NotificationSettingsRequest(
                        notificationEnabled = notification,
                        soundEnabled = sound,
                        vibrationEnabled = vibration
                    )
                )
                if (!response.isSuccessful || response.body()?.success != true) {
                    _toastEvent.value = SettingsToastEvent("설정 저장 실패")
                }
            } catch (_: Exception) {
                _toastEvent.value = SettingsToastEvent("설정 저장 실패")
            }
        }

        when {
            notification != null -> {
                notificationUpdateJob?.cancel()
                notificationUpdateJob = newJob
            }
            sound != null -> {
                soundUpdateJob?.cancel()
                soundUpdateJob = newJob
            }
            vibration != null -> {
                vibrationUpdateJob?.cancel()
                vibrationUpdateJob = newJob
            }
        }
    }

    // 서버 호출이 실패해도 로컬 로그아웃은 항상 진행 (사용자 관점에서 항상 성공해야 함)
    fun logout() {
        viewModelScope.launch {
            try {
                ApiClient.api.logout()
            } catch (_: Exception) {
                // 무시하고 로컬 정리로 진행
            }
            _logoutEvent.value = true
        }
    }

    // 서버 회원탈퇴 성공 시에만 Activity가 로컬 정리를 하도록 결과를 전달
    fun withdraw(userId: String) {
        if (userId.isBlank()) {
            _withdrawResult.value = AuthResultEvent(false, "로그인 정보가 없습니다.")
            return
        }
        viewModelScope.launch {
            try {
                val response = ApiClient.api.deleteUser(userId)
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    _withdrawResult.value = AuthResultEvent(true, "회원탈퇴가 완료되었습니다.")
                } else {
                    val message = ApiClient.parseErrorMessage(response.errorBody(), "회원탈퇴에 실패했습니다.")
                    _withdrawResult.value = AuthResultEvent(false, message)
                }
            } catch (_: Exception) {
                _withdrawResult.value = AuthResultEvent(false, "네트워크 오류로 회원탈퇴에 실패했습니다.")
            }
        }
    }

    fun onToastHandled() {
        _toastEvent.value = null
    }

    fun onLogoutHandled() {
        _logoutEvent.value = null
    }

    fun onWithdrawHandled() {
        _withdrawResult.value = null
    }

    companion object {
        private const val DEFAULT_GUARDIAN_LABEL = "보호자"
    }
}
