package com.example.on_safe.messaging

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import com.example.on_safe.MainActivity
import com.example.on_safe.R
import com.example.on_safe.data.repository.FallLogSource
import com.example.on_safe.ui.notification.NotificationActivity
import com.example.on_safe.util.TokenManager
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

    // 보호자가 받는 피보호자 위험·주의 알림 — 소리·진동이 채널에 고정되므로 별도 채널.
    // 소리·진동(res/values/fall_alert.xml)을 바꾸면 _v 번호를 올려야 설치된 기기에 반영된다.
    // 서버가 백그라운드 알림에도 이 ID를 지정해야 같은 소리·진동이 난다(현재 서버는 onsafe_alerts로 보냄).
    const val CHANNEL_DANGER = "onsafe_danger_v1"   // 위험(낙상·위험 수준): 경보음 + 진동
    const val CHANNEL_WARNING = "onsafe_warning_v1" // 주의: 진동만

    // 같은 이벤트가 연달아 와도 서로 덮지 않도록 표시마다 고유 ID 부여
    private val notificationId = AtomicInteger(1000)

    // 딥링크 PendingIntent requestCode — 목적별 분리
    private const val REQUEST_APP_LAUNCH = 100
    private const val REQUEST_FALL_DEEPLINK = 200

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
        val vibration = fallAlertVibration(context)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DANGER,
                "위험 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "피보호자 낙상·위험 감지 — 경보음과 진동"
                setSound(alarmSoundUri(context), ALARM_ATTRIBUTES)
                enableVibration(true)
                vibrationPattern = vibration
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WARNING,
                "주의 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "피보호자 주의 수준 움직임 — 진동만"
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = vibration
            }
        )
    }

    // 알림 효과음 속성 — 알림 볼륨·방해 금지 설정을 따른다
    private val ALARM_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun alarmSoundUri(context: Context): Uri =
        Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.alarm}")

    private fun fallAlertVibration(context: Context): LongArray =
        context.resources.getIntArray(R.array.fall_alert_vibration_pattern).map { it.toLong() }.toLongArray()

    /** 보호자 위험·주의 알림 구분 — 소리·진동이 달라 채널이 갈린다 */
    enum class FallAlertLevel { DANGER, WARNING }

    /**
     * 피보호자 본인 기기로 온 낙상 계열 알림인지 — data user_id(사고 이력 주인)가 내 userId.
     * 이런 알림은 표시하지 않는다(보호자에게만 알린다). 서버가 피보호자에게도 보내지만, 카메라 모드 기기에서
     * 울리면 낙상 녹화에 녹음되고 본인에게는 필요 없는 알림이다.
     * 백그라운드에선 시스템이 직접 표시해 앱이 막을 수 없다 — 서버가 피보호자에게 보내지 않아야 완전히 막힌다.
     */
    internal fun isOwnFallAlert(event: String?, data: Map<String, String>, myUserId: String): Boolean {
        if (!isFallEvent(event)) return false
        val owner = data["user_id"] ?: return false
        return myUserId.isNotBlank() && owner == myUserId
    }

    /**
     * 서버 낙상 계열 알림을 위험/주의로 나눈다. 해당 없으면 null(기존 안전 알림 채널).
     * - fall_escalated(미확인 위험 재알림) → 위험
     * - fall_detected: fall=true 또는 score > 75 → 위험, 그 외 → 주의(서버는 주의 수준도 같은 이벤트로 보낸다)
     *   score가 없으면 놓치지 않도록 위험으로 본다
     * 피보호자 본인 기기 알림은 [isOwnFallAlert]로 표시 전에 걸러진다.
     */
    internal fun fallAlertLevel(event: String?, data: Map<String, String>, myUserId: String): FallAlertLevel? {
        if (!isFallEvent(event)) return null
        if (isOwnFallAlert(event, data, myUserId)) return null
        if (event == "fall_escalated") return FallAlertLevel.DANGER
        if (data["fall"]?.toBooleanStrictOrNull() == true) return FallAlertLevel.DANGER
        val score = data["score"]?.toFloatOrNull() ?: return FallAlertLevel.DANGER
        return if (score > FallLogSource.DANGER_THRESHOLD) FallAlertLevel.DANGER else FallAlertLevel.WARNING
    }

    /**
     * 이벤트를 트레이 알림으로 표시한다.
     * @param event 서버가 실은 event 코드(pairing_approved / fall_detected / fall_escalated 등).
     * @param title / [body] 서버가 함께 보낸 문구. 없으면 event 코드로 기본 문구를 만든다.
     *
     * 낙상 계열은 알림 목록으로 직행(홈을 백스택에 배치). 그 외는 런처 인텐트로 앱 실행.
     */
    // POST_NOTIFICATIONS 미허용 시 notify는 조용히 무시 — 예외 없음
    @SuppressLint("MissingPermission")
    fun show(
        context: Context,
        event: String?,
        title: String?,
        body: String?,
        data: Map<String, String> = emptyMap()
    ) {
        val level = fallAlertLevel(event, data, TokenManager.getUserId(context))
        val channelId = when (level) {
            FallAlertLevel.DANGER -> CHANNEL_DANGER
            FallAlertLevel.WARNING -> CHANNEL_WARNING
            null -> channelFor(event)
        }
        val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: defaultTitle(event)
        val resolvedBody = body?.takeIf { it.isNotBlank() } ?: defaultBody(event)

        val contentIntent = contentIntentFor(context, event)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(resolvedTitle)
            .setContentText(resolvedBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(resolvedBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .apply { contentIntent?.let { setContentIntent(it) } }
            // Android 8 미만은 채널이 없어 알림마다 소리·진동을 직접 지정한다
            .apply {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O && level != null) {
                    setVibrate(fallAlertVibration(context))
                    if (level == FallAlertLevel.DANGER) setSound(alarmSoundUri(context))
                }
            }
            .build()

        // POST_NOTIFICATIONS(33+) 미허용이면 notify 는 조용히 무시된다 — 크래시 없음.
        NotificationManagerCompat.from(context).notify(notificationId.incrementAndGet(), notification)
    }

    // 낙상 딥링크 — 홈을 백스택에 두어 뒤로가기 시 홈 복귀
    private fun contentIntentFor(context: Context, event: String?): PendingIntent? {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (isFallEvent(event)) {
            TaskStackBuilder.create(context)
                .addNextIntent(Intent(context, MainActivity::class.java))
                .addNextIntent(Intent(context, NotificationActivity::class.java))
                .getPendingIntent(REQUEST_FALL_DEEPLINK, flags)
        } else {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            launch?.let { PendingIntent.getActivity(context, REQUEST_APP_LAUNCH, it, flags) }
        }
    }

    private fun channelFor(event: String?): String =
        if (event != null && event.startsWith("pairing")) CHANNEL_PAIRING else CHANNEL_ALERTS

    // 서버가 표시 문구를 안 실어 보낼 때를 대비한 폴백. 서버 문구가 있으면 그쪽이 우선.
    private fun defaultTitle(event: String?): String = when (event) {
        "pairing_approved" -> "보호자 연결 완료"
        "pairing_rejected" -> "연결 요청 거절됨"
        "pairing_displaced" -> "보호자 연결 해제됨"
        "pairing_unpaired" -> "보호자 연결 해제됨"
        "fall_detected" -> "낙상 감지"
        "fall_escalated" -> "낙상 재알림 — 확인 필요"
        else -> "늘봄 알림"
    }

    private fun defaultBody(event: String?): String = when (event) {
        "pairing_approved" -> "보호자 연결이 완료되었습니다."
        "pairing_rejected" -> "상대방이 연결 요청을 거절했습니다."
        "pairing_displaced" -> "새 연결이 성립되어 기존 연결이 해제되었습니다."
        "pairing_unpaired" -> "보호자 연결이 해제되었습니다."
        "fall_detected" -> "낙상이 감지되었습니다. 즉시 확인해주세요."
        "fall_escalated" -> "아직 확인되지 않은 낙상 알림이 있습니다."
        else -> "새로운 알림이 도착했습니다."
    }
}
