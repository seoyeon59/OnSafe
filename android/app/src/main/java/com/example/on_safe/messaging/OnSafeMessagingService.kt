package com.example.on_safe.messaging

import android.util.Log
import com.example.on_safe.BuildConfig
import com.example.on_safe.ui.pairing.PairingApprovalDialog
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM 수신 진입점.
 *
 * 서버가 보내는 이벤트(pairing_approved / rejected / displaced / unpaired / 오프라인 등)를 받아
 *  1) 트레이 알림으로 표시하고([PushNotifications]),
 *  2) 포그라운드 화면 갱신용 이벤트를 발행한다([PushEventBus]).
 * 단, 실시간 영상 송출 요청(live_request)은 알림 없이 [LiveRequestInbox]로만 보낸다.
 *
 * data 전용 메시지는 앱이 백그라운드일 때도 이 콜백으로 들어온다(프로세스가 살아있는 한).
 * 시스템 자동 표시에 기대지 않고 직접 알림을 만들어, 포그라운드/백그라운드 동작을 일관되게 한다.
 */
class OnSafeMessagingService : FirebaseMessagingService() {

    // 토큰 재발급(설치·데이터 삭제·주기적 롤오버) 시 호출 — 서버와 재동기화.
    override fun onNewToken(token: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "새 FCM 토큰 발급 — 서버 동기화")
        FcmTokenRegistrar.onNewToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        // 서버는 event 코드를 data 로 싣는다. notification 블록이 함께 오면 문구는 그쪽을 우선.
        val event = data["event"] ?: data["type"]
        val title = message.notification?.title ?: data["title"]
        val body = message.notification?.body ?: data["body"] ?: data["message"]

        if (BuildConfig.DEBUG) Log.d(TAG, "푸시 수신 event=$event")

        // 실시간 영상 송출 요청 — data 전용. 트레이 알림·화면 갱신 이벤트 없이 카메라 모드 수신함으로만 보낸다
        if (event == LiveRequestInbox.EVENT) {
            val accepted = LiveRequestInbox.receive(data)
            if (BuildConfig.DEBUG) Log.d(TAG, "live_request ${if (accepted) "보관" else "무시(만료·송출 중)"}")
            return
        }

        PushNotifications.show(applicationContext, event, title, body)
        PushEventBus.publish(PushEvent(event, data))
        // 보호자 연결 요청 — 승인해야 성립하므로 보이는 화면 위에 바로 승인 창
        if (event == PairingApprovalDialog.EVENT) PairingApprovalDialog.offer(data, message.sentTime)
    }

    private companion object {
        const val TAG = "OnSafeFcm"
    }
}
