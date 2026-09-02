package com.example.on_safe.util

import android.app.Activity
import android.content.Intent
import android.net.Uri

/**
 * 약관 페이지 링크 (로그인 / 회원가입 Step1 공용).
 * TODO: URL 확정 후 상수 채우기 — 빈 값이면 openTermsUrl()이 "준비 중" 안내 표시.
 */
object TermsLinks {
    const val SERVICE = ""     // 이용약관
    const val PRIVACY = ""     // 개인정보 수집 및 이용
    const val SENSITIVE = ""   // 민감정보(건강·위치) 처리
    const val MARKETING = ""   // 마케팅 정보 수신
}

/** 약관 URL 열기 — URL 미확정·브라우저 실행 실패 시 공통 안내 */
fun Activity.openTermsUrl(url: String) {
    if (url.isBlank()) {
        toast("약관 페이지가 아직 준비 중입니다.")
        return
    }
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        toast("약관 페이지를 열 수 없습니다.")
    }
}
