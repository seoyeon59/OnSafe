package com.example.on_safe.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 잡히지 않은 예외로 앱이 죽기 직전에 스택트레이스를 파일로 남긴다.
// 카메라 모드 연속 촬영 중 RejectedExecutionException 크래시처럼 재현이 뜸하고
// adb를 그 순간에 연결해두기 어려운 경우, 다음에 기기를 연결했을 때 파일만 꺼내
// 정확한 발생 지점(클래스·줄 번호·호출 스택)을 확인하기 위한 용도.
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
        val dir = File(context.getExternalFilesDir(null), "crash_logs").apply { mkdirs() }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(Date())
        val file = File(dir, "crash_$timestamp.txt")

        val stackTraceWriter = StringWriter()
        throwable.printStackTrace(PrintWriter(stackTraceWriter))

        file.writeText(
            buildString {
                appendLine("시각: $timestamp")
                appendLine("스레드: ${thread.name}")
                appendLine("기기: ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})")
                appendLine()
                append(stackTraceWriter.toString())
            }
        )

        // 오래된 로그부터 정리 — 최근 MAX_LOG_FILES개만 유지
        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_LOG_FILES)
            ?.forEach { it.delete() }
    }
}
