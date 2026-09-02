package com.example.on_safe.ui.login

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.on_safe.BuildConfig
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.LoginRequest
import com.example.on_safe.network.errorMessage
import com.example.on_safe.network.isOk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// 로그인 화면 상태 — 로딩 여부, 인라인 에러 메시지(비었으면 표시 안 함)
data class LoginUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

// 로그인 성공 결과 — 토큰 저장·화면 이동은 Context 필요로 Activity가 담당
data class LoginSuccess(
    val accessToken: String,
    val refreshToken: String,
    val userId: String
)

class LoginViewModel : ViewModel() {

    private val _uiState = MutableLiveData(LoginUiState())
    val uiState: LiveData<LoginUiState> = _uiState

    // 1회성 이벤트 — 소비 후 onLoginHandled()로 다시 null 처리
    private val _loginSuccess = MutableLiveData<LoginSuccess?>()
    val loginSuccess: LiveData<LoginSuccess?> = _loginSuccess

    // id/pw 빈 값 체크·테두리 강조는 순수 View 로직 — Activity가 선처리
    fun login(id: String, password: String, deviceId: String) {
        setState { copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val response = ApiClient.api.login(
                    LoginRequest(userId = id, password = password, deviceId = deviceId)
                )
                val data = response.body()?.data
                if (response.isOk && data != null) {
                    _loginSuccess.value = LoginSuccess(data.accessToken, data.refreshToken, data.userId)
                } else {
                    val message = response.errorMessage("아이디 또는 비밀번호가 올바르지 않습니다.")
                    // 서버 응답 원문은 디버그에서만 — 릴리즈 logcat 노출 방지
                    if (BuildConfig.DEBUG) Log.w("Login", "실패 — HTTP ${response.code()}: $message")
                    setState { copy(errorMessage = message) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e("Login", "네트워크 오류", e)
                setState { copy(errorMessage = "네트워크 오류가 발생했습니다.") }
            } finally {
                setState { copy(isLoading = false) }
            }
        }
    }

    // Activity가 토큰 저장·화면 이동을 마친 뒤 호출 — 재구독 시 재실행 방지
    fun onLoginHandled() {
        _loginSuccess.value = null
    }

    // 오류 문구 표시 후 호출 — 남겨두면 화면 회전 재구독 시 지나간 오류가 되살아남
    fun onErrorShown() {
        setState { copy(errorMessage = null) }
    }

    private inline fun setState(update: LoginUiState.() -> LoginUiState) {
        _uiState.value = (_uiState.value ?: LoginUiState()).update()
    }
}
