package com.example.on_safe.network.dto

data class FallLogResponse(
    val logId: String,
    val deviceId: String,
    val userId: String,
    val score: Float,
    val fall: Boolean,
    val isConfirmed: Boolean,
    val hasVideo: Boolean,
    /**
     * none(주의 등급 등 애초에 영상 미제공) / processing(위험 등급, post-이벤트 녹화·업로드 대기 중) /
     * ready(hasVideo=true와 동일 조건, 재생 가능) — 백엔드 FallLogResponse.videoStatus와 동일.
     *
     * Gson은 생성자를 거치지 않아 기본값이 적용되지 않는다. 응답에 video_status가 없으면
     * non-null로 선언해도 null이 들어오므로 nullable로 두고 매핑 시점에 대체한다.
     */
    val videoStatus: String?,
    val timestamp: String
)

data class ConfirmFallLogResponse(
    val logId: String,
    val isConfirmed: Boolean
)
