package com.example.on_safe.ui.camera

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.on_safe.network.ApiClient
import kotlinx.coroutines.launch

// 사이드 패널 "연결 정보"에 표시할 값 — 카메라/보호자 모드가 같은 계정으로 로그인하는 구조라
// 여기 표시되는 이름도 계정 소유자 본인 이름이다 (별도 "보호자 계정" 개념 없음)
data class CameraModeUiState(
    val guardianName: String = "",
    val deviceId: String = ""
)

class CameraModeViewModel : ViewModel() {

    private val _uiState = MutableLiveData(CameraModeUiState())
    val uiState: LiveData<CameraModeUiState> = _uiState

    // ANDROID_ID는 Context가 필요해 Activity가 계산해서 넘겨준다
    fun setDeviceId(deviceId: String) {
        setState { copy(deviceId = deviceId) }
    }

    fun loadGuardianName(userId: String) {
        if (userId.isBlank()) return
        viewModelScope.launch {
            try {
                val response = ApiClient.api.getUser(userId)
                val body = response.body()
                if (response.isSuccessful && body?.success == true && body.data != null) {
                    setState { copy(guardianName = body.data.name) }
                }
                // 실패 시 아무 것도 하지 않음 — 레이아웃 기본 텍스트가 계속 보임
            } catch (_: Exception) {
                // 조회 실패 → 기본 텍스트 유지
            }
        }
    }

    private inline fun setState(update: CameraModeUiState.() -> CameraModeUiState) {
        _uiState.value = (_uiState.value ?: CameraModeUiState()).update()
    }
}
