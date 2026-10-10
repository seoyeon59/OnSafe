package com.example.on_safe.ui.camera

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.on_safe.BuildConfig
import com.example.on_safe.R

/**
 * 보호자 실시간 시청 시작 알림(소리·진동) — 카메라 모드 "보호자가 보는 중" 배지와 함께 울린다.
 *
 * 리소스만 바꾸면 동작이 바뀐다(코드 수정 불필요) — 설정 안내는 res/values/live_watch_alert.xml 참고.
 * - 소리: res/raw/[SOUND_NAME] 파일이 있을 때만 재생. 이름으로 찾아 파일이 없어도 빌드가 깨지지 않는다
 * - 진동: R.array.live_watch_vibration_pattern
 * - 기기 소리 모드를 따른다: 소리 모드 = 소리+진동, 진동 모드 = 진동만, 무음 = 없음(배지는 항상 표시)
 */
object LiveWatchAlert {

    private const val TAG = "LiveWatchAlert"

    // res/raw 파일명(확장자 제외)
    private const val SOUND_NAME = "live_watch_start"

    // 알림 효과음 — 알림 볼륨을 따르고, 오디오 포커스를 잡지 않아 다른 재생을 멈추지 않는다
    private val SOUND_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun play(context: Context) {
        val appContext = context.applicationContext
        val ringerMode = (appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.ringerMode
            ?: AudioManager.RINGER_MODE_NORMAL
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) return
        if (ringerMode == AudioManager.RINGER_MODE_NORMAL) playSound(appContext)
        vibrate(appContext)
    }

    // 리소스가 없으면 0 — 파일을 넣기 전에도 빌드·실행이 되도록 이름으로 찾는다
    @SuppressLint("DiscouragedApi")
    private fun playSound(context: Context) {
        val resId = context.resources.getIdentifier(SOUND_NAME, "raw", context.packageName)
        if (resId == 0) return
        // 알림은 부가 기능 — 재생 실패가 송출·촬영에 영향을 주지 않게 예외를 흡수한다
        try {
            val afd = context.resources.openRawResourceFd(resId) ?: return
            val player = MediaPlayer()
            afd.use { player.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            player.setAudioAttributes(SOUND_ATTRIBUTES)
            player.setOnCompletionListener { it.release() }
            player.setOnErrorListener { mp, _, _ -> mp.release(); true }
            player.prepare()
            player.start()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "시청 시작 효과음 재생 실패", e)
        }
    }

    private fun vibrate(context: Context) {
        val pattern = try {
            context.resources.getIntArray(R.array.live_watch_vibration_pattern).map { it.toLong() }.toLongArray()
        } catch (_: Exception) {
            return
        }
        // 대기 값만 있거나 비어 있으면 진동할 구간이 없다
        if (pattern.size < 2 || pattern.drop(1).all { it <= 0 }) return

        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "시청 시작 진동 실패", e)
        }
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}
