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
 * 촬영 시작 알림(소리·진동) — 카메라 모드에서 "촬영 시작하기"를 누르면 울린다.
 * 보호자 실시간 시청 시작 때는 울리지 않는다("보호자가 보는 중" 배지·화면 밝힘만).
 *
 * 리소스만 바꾸면 동작이 바뀐다(코드 수정 불필요) — 설정 안내는 res/values/live_watch_alert.xml 참고.
 * - 소리: res/raw/[SOUND_NAME](카메라 셔터음). 이름으로 찾아 파일이 없어도 빌드가 깨지지 않는다
 * - 진동: 셔터음에 맞춘 구간별 길이·세기(R.array.live_watch_vibration_timings / _amplitudes)
 * - 기기 소리 모드를 따른다: 소리 모드 = 소리+진동, 진동 모드 = 진동만, 무음 = 없음
 */
object LiveWatchAlert {

    private const val TAG = "LiveWatchAlert"

    // res/raw 파일명(확장자 제외) — keep.xml의 tools:keep과 같아야 release 축소에서 살아남는다
    private const val SOUND_NAME = "camera_start"

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
        // 알림은 부가 기능 — 재생 실패가 촬영에 영향을 주지 않게 예외를 흡수한다
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
            if (BuildConfig.DEBUG) Log.w(TAG, "촬영 시작 효과음 재생 실패", e)
        }
    }

    private fun vibrate(context: Context) {
        val (timings, amplitudes) = try {
            val res = context.resources
            res.getIntArray(R.array.live_watch_vibration_timings).map { it.toLong() }.toLongArray() to
                res.getIntArray(R.array.live_watch_vibration_amplitudes).map { it.coerceIn(0, 255) }.toIntArray()
        } catch (_: Exception) {
            return
        }
        // 배열 개수가 다르거나 진동할 구간이 없으면 울리지 않는다
        if (timings.isEmpty() || timings.size != amplitudes.size) return
        if (timings.indices.none { timings[it] > 0 && amplitudes[it] > 0 }) return

        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        try {
            when {
                // 세기 조절 가능 — 셔터음의 "톡(약) → 쿵(강) → 잦아듦"을 그대로
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator.hasAmplitudeControl() ->
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                // 세기 조절 불가 — 같은 타이밍의 켜짐·꺼짐으로
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                    vibrator.vibrate(VibrationEffect.createWaveform(toOnOffPattern(timings, amplitudes), -1))
                else -> {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(toOnOffPattern(timings, amplitudes), -1)
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "촬영 시작 진동 실패", e)
        }
    }

    /**
     * 구간별 세기 → 켜짐·꺼짐 패턴("대기, 진동, 대기, …"). 세기 0은 꺼짐, 나머지는 켜짐이며
     * 같은 상태가 이어지면 합친다. 예) [10,30,20,50,50,70]/[0,110,0,255,150,60] → [10,30,20,170]
     */
    internal fun toOnOffPattern(timings: LongArray, amplitudes: IntArray): LongArray {
        val out = mutableListOf<Long>()
        var on = false   // 패턴은 항상 "대기"부터 시작
        var acc = 0L
        for (i in timings.indices) {
            val segOn = amplitudes[i] > 0
            if (segOn == on) {
                acc += timings[i]
            } else {
                out.add(acc)
                acc = timings[i]
                on = segOn
            }
        }
        out.add(acc)
        return out.toLongArray()
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}
