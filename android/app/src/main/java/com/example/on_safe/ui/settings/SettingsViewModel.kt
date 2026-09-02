package com.example.on_safe.ui.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.NotificationSettingsRequest
import com.example.on_safe.network.dto.NotificationSettingsResponse
import com.example.on_safe.util.DisplayText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// 저장 실패 등 1회성 토스트 메시지
data class SettingsToastEvent(val message: String)

// 로그아웃·회원탈퇴 결과 — 토큰 정리·화면 이동은 Activity 담당이라 성공 여부와 메시지만 전달
data class AuthResultEvent(val success: Boolean, val message: String)

// 생성자 기본값 파라미터 금지 — by viewModels()의 무인자 생성자 탐색 실패 원인.
// 의존성은 필드 초기값으로 지정.
class SettingsViewModel : ViewModel() {

    private val _userName = MutableLiveData<String>()
    val userName: LiveData<String> = _userName

    // 서버 알림 설정 — Activity가 캐시·스위치·아이콘에 반영
    private val _serverSettings = MutableLiveData<NotificationSettingsResponse?>()
    val serverSettings: LiveData<NotificationSettingsResponse?> = _serverSettings

    private val _toastEvent = MutableLiveData<SettingsToastEvent?>()
    val toastEvent: LiveData<SettingsToastEvent?> = _toastEvent

    // 1회성 이벤트 — true면 로그아웃 완료, Activity가 소비 후 null로 리셋
    private val _logoutEvent = MutableLiveData<Boolean?>()
    val logoutEvent: LiveData<Boolean?> = _logoutEvent

    private val _withdrawResult = MutableLiveData<AuthResultEvent?>()
    val withdrawResult: LiveData<AuthResultEvent?> = _withdrawResult

    // 연속 토글 시 이전 PUT이 늦게 도착해 최신 상태를 덮어쓰는 것 방지 —
    // 항목별 Job을 두고 같은 항목이 다시 바뀔 때만 이전 요청 취소
    private var notificationUpdateJob: Job? = null
    private var soundUpdateJob: Job? = null
    private var vibrationUpdateJob: Job? = null

    // 이름 누락 시 "보호자님" 호칭만 남는 문제 — DisplayText가 문장 단위 대체
    fun loadUserName(userId: String) {
        if (userId.isBlank()) {
            // 로그인 정보가 없을 때(사실상 도달하지 않는 방어 경로)의 기본 표시값
            _userName.value = DisplayText.guardianTitle(null)
            return
        }
        // 이미 표시 중인 이름이 있으면 유지 — onResume 재조회마다 깜빡이는 문제 방지
        if (_userName.value.isNullOrBlank()) _userName.value = DisplayText.LOADING
        viewModelScope.launch {
            val name = try {
                val response = ApiClient.api.getUser(userId)
                val body = response.body()
                if (response.isSuccessful && body?.success == true) body.data?.name else null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            _userName.value = DisplayText.guardianTitle(name)
        }
    }

    // 서버 최신 값 조회 — 실패 시 미방출로 Activity의 캐시 표시 유지
    fun loadNotificationSettings(userId: String) {
        if (userId.isBlank()) return
        viewModelScope.launch {
            try {
                val response = ApiClient.api.getNotificationSettings(userId)
                val body = response.body()
                if (response.isSuccessful && body?.success == true && body.data != null) {
                    _serverSettings.value = body.data
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 조회 실패 → 미방출
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
            } catch (e: CancellationException) {
                // 아래에서 같은 항목의 이전 요청을 의도적으로 취소한다 —
                // 이를 실패로 처리하면 토글을 빠르게 두 번 누를 때마다 "저장 실패"가 뜬다
                throw e
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

    // 서버 호출 실패와 무관하게 로컬 로그아웃은 항상 진행
    fun logout(refreshToken: String?) {
        viewModelScope.launch {
            try {
                ApiClient.api.logout(refreshToken)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 무시하고 로컬 정리로 진행
            }
            _logoutEvent.value = true
        }
    }

    // 서버 탈퇴 성공 시에만 Activity가 로컬 정리하도록 결과 전달
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
            } catch (e: CancellationException) {
                throw e
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
}
