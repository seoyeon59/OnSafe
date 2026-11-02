package com.example.on_safe.messaging

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.on_safe.BuildConfig

/**
 * 위험 알림을 무음·진동 모드에서도 울린다.
 *
 * 알림 채널의 소리·진동은 벨소리 모드를 따른다 — 무음이면 소리·진동 모두, 진동 모드면 소리가 꺼진다.
 * 낙상은 놓치면 안 되므로 그때만 앱이 알람 용도(USAGE_ALARM)로 경보음·진동을 직접 낸다.
 * 알람 용도는 벨소리 모드에 막히지 않고 알람 볼륨을 따른다.
 * - 소리 모드: 채널이 울리므로 아무것도 하지 않는다(중복 방지)
 * - 진동 모드: 채널이 진동하므로 소리만 낸다
 * - 무음 모드: 소리·진동 모두 낸다
 * - 방해 금지 모드: 사용자가 명시적으로 막은 것이라 채널 설정(bypassDnd=false)을 따라 울리지 않는다
 *
 * 앱이 백그라운드여도 동작하려면 서버가 위험 알림을 data 전용으로 보내야 한다(notification 메시지는
 * 백그라운드에서 시스템이 표시해 이 코드가 실행되지 않는다).
 */
internal object MutedModeFallAlarm {

    private const val TAG = "MutedModeFallAlarm"

    private val ALARM_AUDIO: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    // 재생 중 GC로 소리가 끊기지 않게 참조를 잡아 둔다. 연달아 오면 이전 재생은 끊고 새로 울린다
    private var player: MediaPlayer? = null

    fun playIfMuted(context: Context) {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        val ringerMode = audio.ringerMode
        if (ringerMode == AudioManager.RINGER_MODE_NORMAL) return
        val notifications = context.getSystemService(NotificationManager::class.java)
        if (notifications != null &&
            notifications.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
        ) return

        playSound(context.applicationContext)
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) vibrate(context)
    }

    @Synchronized
    private fun playSound(context: Context) {
        player?.release()
        player = try {
            MediaPlayer().apply {
                setAudioAttributes(ALARM_AUDIO)
                setDataSource(context, PushNotifications.alarmSoundUri(context))
                setOnCompletionListener { release(it) }
                setOnErrorListener { mp, _, _ -> release(mp); true }
                prepare()   // 앱 리소스라 바로 끝난다
                start()
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "경보음 재생 실패", e)
            null
        }
    }

    @Synchronized
    private fun release(mp: MediaPlayer) {
        mp.release()
        if (player === mp) player = null
    }

    private fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        val pattern = PushNotifications.fallAlertVibration(context)
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> vibrator.vibrate(
                    VibrationEffect.createWaveform(pattern, -1),
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)
                )
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> @Suppress("DEPRECATION")
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1), ALARM_AUDIO)
                else -> @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1, ALARM_AUDIO)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "진동 실패", e)
        }
    }
}
