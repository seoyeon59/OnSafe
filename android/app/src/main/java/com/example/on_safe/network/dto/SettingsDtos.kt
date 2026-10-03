package com.example.on_safe.network.dto

// 부분 수정 요청 — null 항목은 직렬화에서 제외되어 서버 값이 유지된다 (UserUpdateRequest와 동일 규칙)
data class NotificationSettingsRequest(
    val notificationEnabled: Boolean? = null,
    val soundEnabled: Boolean? = null,
    val vibrationEnabled: Boolean? = null
)

data class NotificationSettingsResponse(
    val notificationEnabled: Boolean,
    val soundEnabled: Boolean,
    val vibrationEnabled: Boolean
)

// 마케팅 수신 동의 — GET/PUT /api/settings/marketing/{userId} (v4.2 신규)
data class MarketingConsentRequest(val consent: Boolean)

data class MarketingConsentResponse(
    val consent: Boolean,
    // 미동의 상태면 시점이 없음
    val consentedAt: String?
)

// 필수 약관 재동의 (v4.13) — 로그인 응답 pending_consents·pending 조회·재동의 응답 공용.
// required=false는 경미한 개정(확인만), 서버 차단 대상 아님
data class PendingConsent(
    val type: String,
    val version: String,
    val required: Boolean
)

// version은 pending에서 받은 값 그대로 — 다르면 409 CONSENT_VERSION_MISMATCH
data class ConsentAgreeRequest(val consents: List<ConsentAgreeItem>)

data class ConsentAgreeItem(val type: String, val version: String)
