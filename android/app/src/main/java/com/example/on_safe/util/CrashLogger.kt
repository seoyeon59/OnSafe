package com.example.on_safe.util

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 앱이 죽기 직전 스택트레이스를 파일로 남긴다 — adb 미연결 상태에서 발생한 크래시를
// 나중에 꺼내보기 위한 용도 (Android/data/<package>/files/crash_logs/)
object CrashLogger {

    // 크래시가 반복돼도 저장공간을 계속 차지하지 않도록 최근 것만 유지
    private const val MAX_LOG_FILES = 5

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeLog(appContext, thread, throwable)
            } catch (_: Exception) {
                // 로그 남기기 자체가 실패해도 원래 종료 흐름은 그대로 진행
            }
            // 시스템 기본 처리(프로세스 종료 등)는 그대로 이어감 — 로그만 가로채서 남길 뿐
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeLog(context: Context, thread: Thread, throwable: Throwable) {
        // 외부 저장소 미탑재 시 내부 저장소로 — 상대 경로로 떨어져 쓰기 실패하는 것 방지
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(baseDir, "crash_logs").apply { mkdirs() }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(Date())

        File(dir, "crash_$timestamp.txt").writeText(
            buildString {
                appendLine("시각: $timestamp")
                appendLine("스레드: ${thread.name}")
                appendLine("기기: ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})")
                appendLine()
                append(throwable.stackTraceToString())
            }
        )

        // 오래된 로그부터 정리 — 최근 MAX_LOG_FILES개만 유지
        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_LOG_FILES)
            ?.forEach { it.delete() }
    }
}
