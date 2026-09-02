package com.example.on_safe.util

/**
 * 서버 값 부재·조회 실패 시의 공통 대체 문구.
 * 값만 비면 "보호자님"처럼 호칭만 남아 정상/오류 구분 불가 — 문장 단위 대체가 목적.
 */
object DisplayText {

    // 조회 진행 중 — 실패와의 구분용
    const val LOADING = "불러오는 중…"

    // 조회 실패·값 없음 (ID처럼 짧은 값 자리)
    const val NONE = "정보 없음"

    // 목록의 좁은 시각·날짜 칸 — 기호로 자리만 유지
    const val NO_TIME = "--:--"
    const val NO_DATE = "----.--.--"

    // 위험 지수 자리 — 0·이전 점수 잔존 시 "정상" 오독 방지
    const val NO_SCORE = "--"

    // 정상/주의/위험 어느 쪽도 단정할 수 없는 상태의 배지 문구
    const val UNKNOWN_LEVEL = "확인 중"

    /** "홍길동 보호자님" 자리 — 이름 누락 시 계정 표기로 대체 */
    fun guardianTitle(name: String?): String =
        if (name.isNullOrBlank()) "보호자 계정" else "$name 보호자님"

    /** 보호자 홈 기기 ID 자리 */
    fun deviceIdLabel(deviceId: String?): String =
        if (deviceId.isNullOrBlank()) "기기 미등록" else "기기 ID · $deviceId"

    /** 원본 값 노출 자리(카메라 패널 이름·기기 ID 등) — null=조회 전, 빈 값=실패 */
    fun loadingOrNone(value: String?): String = when {
        value == null -> LOADING
        value.isBlank() -> NONE
        else -> value
    }
}
