package com.example.on_safe.ui.camera

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.on_safe.BuildConfig
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.DeviceRegisterRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// 사이드 패널 "연결 정보"에 표시할 값 — 카메라/보호자 모드가 같은 계정으로 로그인하는 구조라
// 여기 표시되는 이름도 계정 소유자 본인 이름이다 (별도 "보호자 계정" 개념 없음).
// 조회 전/실패 구분용 null 유지 — 문구 결정은 DisplayText 담당
data class CameraModeUiState(
    val guardianName: String? = null,
    val deviceId: String? = null
)

class CameraModeViewModel : ViewModel() {

    private val _uiState = MutableLiveData(CameraModeUiState())
    val uiState: LiveData<CameraModeUiState> = _uiState

    // ANDROID_ID는 Context가 필요해 Activity가 계산해서 넘겨준다
    fun setDeviceId(deviceId: String) {
        setState { copy(deviceId = deviceId) }
    }

    // 실패 시에도 상태 갱신 — 레이아웃 예시 문구 잔존 방지
    fun loadGuardianName(userId: String) {
        if (userId.isBlank()) {
            setState { copy(guardianName = "") }
            return
        }
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
            setState { copy(guardianName = name.orEmpty()) }
        }
    }

    // 보호자 홈 조회용 기기 등록 — 서버 upsert라 재진입 시 중복 없음
    fun registerDevice(userId: String, deviceId: String, deviceName: String) {
        if (userId.isBlank() || deviceId.isBlank()) return
        viewModelScope.launch {
            try {
                ApiClient.aiApi.registerDevice(
                    userId,
                    DeviceRegisterRequest(deviceId = deviceId, deviceName = deviceName)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 등록 실패해도 촬영은 무관 — 보호자 홈 기기 ID만 공란
                if (BuildConfig.DEBUG) Log.w("CameraMode", "기기 등록 실패", e)
            }
        }
    }

    private inline fun setState(update: CameraModeUiState.() -> CameraModeUiState) {
        _uiState.value = (_uiState.value ?: CameraModeUiState()).update()
    }
}
