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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// 사이드 패널 "연결 정보"에 표시할 값 — 카메라/보호자 모드가 같은 계정으로 로그인하는 구조라
// 여기 표시되는 이름도 계정 소유자 본인 이름이다 (별도 "보호자 계정" 개념 없음).
// 조회 전/실패 구분용 null 유지 — 문구 결정은 DisplayText 담당
data class CameraModeUiState(
    val guardianName: String? = null,
    val deviceId: String? = null,
    // 페어링 코드 오버레이 — null 이면 로딩 중, 값이 있으면 화면 상단에 표시.
    // 서버 TTL(5분)에 맞춰 자동 재발급되므로 만료 케이스 UI 처리 불필요.
    val pairingCode: String? = null
)

class CameraModeViewModel : ViewModel() {

    private val _uiState = MutableLiveData(CameraModeUiState())
    val uiState: LiveData<CameraModeUiState> = _uiState

    // 페어링 코드 자동 재발급 루프 — 중복 실행 방지용 참조
    private var pairingCodeJob: Job? = null

    // ANDROID_ID는 Context가 필요해 Activity가 계산해서 넘겨준다
    fun setDeviceId(deviceId: String) {
        setState { copy(deviceId = deviceId) }
    }

    // 피보호자용 페어링 코드 자동 발급/재발급 루프.
    // 서버 TTL(5분)에 맞춰 만료 직전에 새 코드로 교체하므로 사용자 조작 없이 항상 유효한 코드 유지.
    // 재진입 시 중복 루프 방지를 위해 기존 Job은 취소하고 재시작.
    fun startPairingCodeAutoRefresh(userId: String) {
        if (userId.isBlank()) return
        // 화면 재생성 시에도 onCreate에서 다시 호출된다. 그때마다 새로 시작하면 실패 횟수가
        // 0으로 돌아가 백오프가 풀리고, 한도가 소진된 상태에서 즉시 재요청하게 된다.
        if (pairingCodeJob?.isActive == true) return
        // TODO: [페어링] 이미 보호자와 연결된 기기는 코드를 띄울 이유가 없는데도 계속 발급된다.
        //       화면에 쓸모없는 코드가 노출되고 서버 발급 한도(시간당 5회)만 소진한다.
        //       판정하려면 "이 피보호자에게 연결된 보호자" 조회 API가 필요하다 —
        //       현재 서버에는 보호자→피보호자 방향(GET /api/guardian/{userId}/wards)만 있음.
        pairingCodeJob = viewModelScope.launch {
            var failures = 0
            while (isActive) {
                val ttlSeconds = fetchAndUpdatePairingCode(userId)
                val delayMs = if (ttlSeconds > 0) {
                    failures = 0
                    // TTL 만료 직전(-10초 안전 여유)에 재발급
                    (ttlSeconds - 10).coerceAtLeast(10) * 1000L
                } else {
                    // 실패는 대개 발급 한도 초과다. 고정 간격으로 계속 두드리면 한도가 풀리지
                    // 않은 채 요청만 쌓이므로 간격을 늘려가며 재시도한다 (30초 → 최대 10분).
                    failures++
                    (RETRY_BASE_MS * (1L shl (failures - 1).coerceAtMost(5))).coerceAtMost(RETRY_MAX_MS)
                }
                delay(delayMs)
            }
        }
    }


    // 한 번의 코드 발급 요청 — TTL 초 반환, 실패 시 -1
    private suspend fun fetchAndUpdatePairingCode(userId: String): Long {
        return try {
            val response = ApiClient.api.issuePairingCode(userId)
            val body = response.body()
            if (response.isSuccessful && body?.success == true && body.data != null) {
                setState { copy(pairingCode = body.data.code) }
                body.data.expiresInSeconds
            } else {
                if (BuildConfig.DEBUG) Log.w("CameraMode", "페어링 코드 발급 실패 code=${response.code()}")
                -1L
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w("CameraMode", "페어링 코드 발급 예외", e)
            -1L
        }
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

    private companion object {
        const val RETRY_BASE_MS = 30_000L
        const val RETRY_MAX_MS = 600_000L
    }
}
