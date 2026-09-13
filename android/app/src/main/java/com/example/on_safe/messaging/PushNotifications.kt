package com.example.on_safe.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.on_safe.R
import java.util.concurrent.atomic.AtomicInteger

/**
 * 푸시 알림 채널 생성·표시 담당.
 *
 * 서버는 승인/거부/오프라인 등 이벤트를 FCM data 메시지로 보낸다(ApiService 주석 참고).
 * data 메시지는 앱이 포그라운드/백그라운드 어디에 있든 [OnSafeMessagingService.onMessageReceived]
 * 로 들어오므로, 트레이 알림은 이 클래스가 직접 만든다(시스템 자동 표시에 의존하지 않음).
 */
object PushNotifications {

    // 채널은 한 번 만들어지면 중요도·이름을 코드로 못 바꾼다(사용자 설정 우선).
    // 나중에 조정이 필요하면 새 ID를 발급해야 하므로 ID에 버전 여지를 남긴다.
    const val CHANNEL_ALERTS = "onsafe_alerts"      // 낙상·오프라인 등 즉시 확인이 필요한 안전 알림
    const val CHANNEL_PAIRING = "onsafe_pairing"    // 보호자 연결(승인·거부·해제) 상태 변화

    // 같은 이벤트가 연달아 와도 서로 덮지 않도록 표시마다 고유 ID 부여
    private val notificationId = AtomicInteger(1000)

    /** Application.onCreate 에서 1회 호출 — 채널이 없으면 만들고, 있으면 그대로 둔다. */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                "안전 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "낙상 감지·연결 끊김 등 즉시 확인이 필요한 알림" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PAIRING,
                "보호자 연결",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "보호자 연결 요청 승인·거부·해제 알림" }
        )
    }

    /**
     * 이벤트를 트레이 알림으로 표시한다.
     * @param event 서버가 실은 event 코드(pairing_approved 등). 채널·기본 문구 결정에 사용.
     * @param title / [body] 서버가 함께 보낸 문구. 없으면 event 코드로 기본 문구를 만든다.
     *
     * 탭하면 런처 인텐트로 앱을 연다 — 로그인/자동로그인 라우팅을 그대로 타 세션이 없을 때도 안전.
     */
    fun show(context: Context, event: String?, title: String?, body: String?) {
        val channelId = channelFor(event)
        val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: defaultTitle(event)
        val resolvedBody = body?.takeIf { it.isNotBlank() } ?: defaultBody(event)

        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = launch?.let {
            android.app.PendingIntent.getActivity(
                context,
                0,
                it,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(resolvedTitle)
            .setContentText(resolvedBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(resolvedBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .apply { contentIntent?.let { setContentIntent(it) } }
            .build()

        // POST_NOTIFICATIONS(33+) 미허용이면 notify 는 조용히 무시된다 — 크래시 없음.
        NotificationManagerCompat.from(context).notify(notificationId.incrementAndGet(), notification)
    }

    private fun channelFor(event: String?): String =
        if (event != null && event.startsWith("pairing")) CHANNEL_PAIRING else CHANNEL_ALERTS

    // 서버가 표시 문구를 안 실어 보낼 때를 대비한 폴백. 서버 문구가 있으면 그쪽이 우선.
    private fun defaultTitle(event: String?): String = when (event) {
        "pairing_approved" -> "보호자 연결 완료"
        "pairing_rejected" -> "연결 요청 거절됨"
        "pairing_displaced" -> "보호자 연결 해제됨"
        "pairing_unpaired" -> "보호자 연결 해제됨"
        else -> "늘봄 알림"
    }

    private fun defaultBody(event: String?): String = when (event) {
        "pairing_approved" -> "보호자 연결이 완료되었습니다."
        "pairing_rejected" -> "상대방이 연결 요청을 거절했습니다."
        "pairing_displaced" -> "새 연결이 성립되어 기존 연결이 해제되었습니다."
        "pairing_unpaired" -> "보호자 연결이 해제되었습니다."
        else -> "새로운 알림이 도착했습니다."
    }
}
