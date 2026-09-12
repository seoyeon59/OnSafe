package com.example.on_safe.ui.camera

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.on_safe.BuildConfig
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.DeviceRegisterRequest
import com.example.on_safe.network.dto.HeartbeatRequest
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
    // 페어링 코드 오버레이 — null 이면 로딩 중 or 이미 페어링됨, 값이 있으면 화면 상단에 표시.
    // 서버 TTL(15분)에 맞춰 자동 재발급되므로 만료 케이스 UI 처리 불필요.
    val pairingCode: String? = null,
    // 이미 보호자와 연결됐는지 — 카메라 모드 진입 시 my-guardian 조회로 판정.
    // true 면 코드 자동 갱신을 스킵하고 오버레이 대신 "○○님과 연결됨" 표시.
    val isPaired: Boolean = false,
    // 연결된 보호자 정보 (isPaired=true 일 때만 유효). userId 는 unpair API 호출에 필요.
    val pairedGuardianUserId: String? = null,
    val pairedGuardianName: String? = null,
)

class CameraModeViewModel : ViewModel() {

    private val _uiState = MutableLiveData(CameraModeUiState())
    val uiState: LiveData<CameraModeUiState> = _uiState

    // 페어링 코드 자동 재발급 루프 — 중복 실행 방지용 참조
    private var pairingCodeJob: Job? = null

    // Heartbeat 전송 루프 — 카메라 모드 켜져 있는 동안만 활성.
    // 백엔드 워치독이 6분 이상 미수신 시 오프라인 판정하므로 2분 주기면 3회 미스가 있어야 오프라인 낙인.
    private var heartbeatJob: Job? = null

    // ANDROID_ID는 Context가 필요해 Activity가 계산해서 넘겨준다
    fun setDeviceId(deviceId: String) {
        setState { copy(deviceId = deviceId) }
    }

    // 피보호자용 페어링 코드 자동 발급/재발급 루프.
    // 서버 TTL(15분)에 맞춰 만료 직전에 새 코드로 교체하므로 사용자 조작 없이 항상 유효한 코드 유지.
    // 이미 도는 루프가 있으면 그대로 둔다 — 재시작하면 백오프가 풀린다(아래 참고).
    //
    // 시작 전에 my-guardian 을 조회해 이미 페어링됐으면 코드 발급 자체를 건너뛴다. 백엔드 정책상
    // 이미 페어링된 elder 는 issuePairingCode 가 거부되므로, 어차피 발급되지도 않을 요청을
    // 반복하며 rate limit 을 소진하는 걸 방지.
    fun startPairingCodeAutoRefresh(userId: String) {
        if (userId.isBlank()) return
        // 화면 재생성 시에도 onCreate에서 다시 호출된다. 그때마다 새로 시작하면 실패 횟수가
        // 0으로 돌아가 백오프가 풀리고, 한도가 소진된 상태에서 즉시 재요청하게 된다.
        if (pairingCodeJob?.isActive == true) return
        pairingCodeJob = viewModelScope.launch {
            // 진입 시점 페어링 확인 — 이미 연결된 상태면 코드 발급 루프 자체를 안 돌린다.
            if (checkAlreadyPaired(userId)) return@launch

            var failures = 0
            while (isActive) {
                val ttlSeconds = fetchAndUpdatePairingCode(userId)
                val delayMs = if (ttlSeconds > 0) {
                    failures = 0
                    // TTL 만료 직전(-10초 안전 여유)에 재발급
                    (ttlSeconds - 10).coerceAtLeast(10) * 1000L
                } else {
                    // 재발급은 만료 10초 전에 돈다. 실패하면 화면의 코드도 곧 만료되므로 지운다 —
                    // 남겨두면 어르신이 죽은 코드를 불러주고 보호자는 이유 없이 계속 실패한다.
                    setState { copy(pairingCode = null) }
                    // 실패는 대개 발급 한도 초과다. 고정 간격으로 계속 두드리면 한도가 풀리지
                    // 않은 채 요청만 쌓이므로 간격을 늘려가며 재시도한다 (30초 → 최대 10분).
                    failures++
                    (RETRY_BASE_MS * (1L shl (failures - 1).coerceAtMost(5))).coerceAtMost(RETRY_MAX_MS)
                }
                delay(delayMs)
            }
        }
    }

    // my-guardian 조회로 페어링 여부 확인. 성공 시 UI 상태 반영하고 true 리턴.
    // 네트워크 실패는 "판정 불가"로 보고 false 리턴 — 코드 발급을 시도해 서버 검증에 맡긴다.
    private suspend fun checkAlreadyPaired(userId: String): Boolean {
        return try {
            val response = ApiClient.api.getMyGuardian(userId)
            val body = response.body()
            if (response.isSuccessful && body?.success == true) {
                val guardian = body.data
                if (guardian != null) {
                    setState {
                        copy(
                            isPaired = true,
                            pairedGuardianUserId = guardian.userId,
                            pairedGuardianName = guardian.name,
                            pairingCode = null,
                        )
                    }
                    true
                } else false
            } else false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
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

    // 카메라 모드 켜져 있는 동안 서버에 살아있음을 알린다. 절전모드 여부도 함께 전송 —
    // 백엔드가 판정 신뢰도 표시나 워치독 처리에 활용 가능.
    // 이미 도는 루프가 있으면 재시작 안 함(중복 요청 방지).
    fun startHeartbeat(isPowerSaveMode: () -> Boolean) {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = viewModelScope.launch {
            while (isActive) {
                try {
                    ApiClient.api.heartbeat(HeartbeatRequest(powerSaveMode = isPowerSaveMode()))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // heartbeat 실패는 사용자에게 노출하지 않는다 — 다음 사이클에서 자연 복구.
                    if (BuildConfig.DEBUG) Log.w("CameraMode", "heartbeat 실패", e)
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    // 페어링 해제 — 양방향(피보호자·보호자 어느 쪽에서 호출해도 동작).
    // 성공 시 콜백으로 UI 갱신(코드 오버레이 다시 표시 등)을 알리고, 실패는 콜백에 예외 메시지 전달.
    fun unpair(userId: String, counterpartUserId: String, onResult: (Boolean, String?) -> Unit) {
        if (userId.isBlank() || counterpartUserId.isBlank()) {
            onResult(false, "연결 정보를 확인할 수 없어요.")
            return
        }
        viewModelScope.launch {
            try {
                val response = ApiClient.api.unpair(userId, counterpartUserId)
                if (response.isSuccessful && response.body()?.success == true) {
                    // 해제 후엔 다시 미연결 상태 — 코드 오버레이 다시 나타남.
                    setState { copy(isPaired = false, pairedGuardianUserId = null, pairedGuardianName = null) }
                    // 자동 재발급 루프 재시작 유도용으로 기존 job 취소
                    pairingCodeJob?.cancel()
                    pairingCodeJob = null
                    onResult(true, null)
                } else {
                    onResult(false, response.body()?.message ?: "연결 해제에 실패했어요.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onResult(false, "네트워크 오류가 발생했어요.")
            }
        }
    }

    private inline fun setState(update: CameraModeUiState.() -> CameraModeUiState) {
        _uiState.value = (_uiState.value ?: CameraModeUiState()).update()
    }

    private companion object {
        const val RETRY_BASE_MS = 30_000L
        const val RETRY_MAX_MS = 600_000L
        // 2분 — 백엔드 오프라인 임계(6분) 대비 3배 여유. 배터리 소모 vs 감지 지연 트레이드오프에서
        // 안전 서비스 특성상 감지 지연 최소화 쪽에 무게.
        const val HEARTBEAT_INTERVAL_MS = 120_000L
    }
}
