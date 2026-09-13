package com.example.on_safe.messaging

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 서버가 실어 보낸 event 코드 + 부가 data. UI 재조회 트리거에 쓴다. */
data class PushEvent(val event: String?, val data: Map<String, String>)

/**
 * 포그라운드 화면용 인프로세스 이벤트 버스.
 *
 * 트레이 알림([PushNotifications])과 별개로, 앱이 떠 있을 때 즉시 화면을 갱신하려는 목적.
 * 예) 보호자 홈이 떠 있는 상태에서 pairing_approved 가 오면 getWards 재조회로 모달을 자동 반영.
 *
 * 구독 예:
 * ```
 * lifecycleScope.launch {
 *     repeatOnLifecycle(Lifecycle.State.STARTED) {
 *         PushEventBus.events.collect { e -> if (e.event == "pairing_approved") reload() }
 *     }
 * }
 * ```
 * 구독자가 없을 때 온 이벤트는 흘려보낸다(트레이 알림이 이미 사용자에게 도달하므로 유실돼도 무방).
 */
object PushEventBus {
    // replay=0: 지난 이벤트를 새 구독자에게 다시 주지 않는다(자동로그인 직후 옛 이벤트 재생 방지).
    // extraBufferCapacity>0: 비-suspend 컨텍스트(서비스)에서 tryEmit 이 실패하지 않도록 여유 버퍼.
    private val _events = MutableSharedFlow<PushEvent>(replay = 0, extraBufferCapacity = 16)
    val events: SharedFlow<PushEvent> = _events.asSharedFlow()

    fun publish(event: PushEvent) {
        _events.tryEmit(event)
    }
}
