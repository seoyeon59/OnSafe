package com.example.on_safe.network

import com.example.on_safe.network.dto.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Kotlin 백엔드 REST 계약.
 * 경로·메서드·DTO가 서버 스펙과 1:1 대응 — 변경 시 서버와 함께 맞출 것.
 */
interface ApiService {

    // ===== Auth =====

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<LoginResponse>>

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<ApiResponse<Unit>>

    @POST("api/auth/check-id")
    suspend fun checkId(@Body request: CheckIdRequest): Response<ApiResponse<Unit>>

    @POST("api/auth/check-mail")
    suspend fun checkMail(@Body request: CheckMailRequest): Response<ApiResponse<Unit>>

    @POST("api/auth/find-id")
    suspend fun findId(@Body request: FindIdRequest): Response<ApiResponse<FindIdResponse>>

    // 아이디+이름+이메일이 모두 일치하면 재설정용 1회용 티켓을 발급받는다
    @POST("api/auth/verify-reset-identity")
    suspend fun verifyResetIdentity(@Body request: VerifyResetIdentityRequest): Response<ApiResponse<VerifyResetIdentityResponse>>

    @POST("api/auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): Response<ApiResponse<Unit>>

    // 두 토큰을 모두 명시 전달한다. 자동 부착에 맡기면 호출부가 로컬 정리를 먼저 한 경우
    // Authorization이 비어 나가 access 토큰이 블랙리스트에 오르지 않는다.
    // 본문의 FCM 토큰은 서버가 세션 종료와 함께 해제 — 로그아웃 뒤 별도 해제 호출은 항상 401
    @POST("api/auth/logout")
    suspend fun logout(
        @Header("Authorization") bearer: String? = null,
        @Header("Refresh-Token") refreshToken: String? = null,
        @Body request: LogoutRequest = LogoutRequest(null, null)
    ): Response<ApiResponse<Unit>>

    @POST("api/auth/refresh")
    suspend fun refresh(@Header("Refresh-Token") refreshToken: String): Response<ApiResponse<TokenResponse>>

    // 자동 로그인 진입 전 서버 세션 검증 — 로컬 만료 30일 제한만으로는 회원탈퇴/강제로그아웃 후
    // 로컬 토큰이 살아있으면 진입이 가능해지므로 서버 블랙리스트까지 확인한다.
    // 200 OK = 유효, 401 = 무효/블랙리스트.
    // 토큰은 authInterceptor가 붙인다 — @Header로 또 지정하면 Authorization이 두 개가 된다.
    @POST("api/auth/validate")
    suspend fun validateToken(): Response<ApiResponse<Unit>>

    // ===== User =====

    @GET("api/users/{userId}")
    suspend fun getUser(@Path("userId") userId: String): Response<ApiResponse<UserResponse>>

    @PUT("api/users/{userId}")
    suspend fun updateUser(
        @Path("userId") userId: String,
        @Body request: UserUpdateRequest
    ): Response<ApiResponse<UserResponse>>

    @POST("api/users/{userId}/verify-password")
    suspend fun verifyPassword(
        @Path("userId") userId: String,
        @Body request: VerifyPasswordRequest
    ): Response<ApiResponse<VerifyPasswordResponse>>

    // DELETE + 바디 — 재인증 티켓 첨부(@HTTP hasBody)
    @HTTP(method = "DELETE", path = "api/users/{userId}", hasBody = true)
    suspend fun deleteUser(
        @Path("userId") userId: String,
        @Body request: DeleteUserRequest
    ): Response<ApiResponse<Unit>>

    // ===== Push (FCM 토큰) =====

    // 기기별 저장(계정당 최대 5대). 해제는 로그아웃 본문(LogoutRequest)이 대신함
    @POST("api/users/{userId}/fcm-token")
    suspend fun registerFcmToken(
        @Path("userId") userId: String,
        @Body request: FcmTokenRequest
    ): Response<ApiResponse<Unit>>

    // ===== Guardian (보호자 페어링) =====

    // 피보호자가 6자리 페어링 코드 발급 — 5분 TTL, 재발급 시 이전 코드는 즉시 무효화됨
    @POST("api/guardian/{userId}/pairing-code")
    suspend fun issuePairingCode(
        @Path("userId") userId: String
    ): Response<ApiResponse<PairingCodeResponse>>

    // 보호자가 코드 입력해서 페어링 요청 — "승인 대기" 상태로 시작한다.
    // 실제 관계 성립은 피보호자가 approve 하거나 reject 해야 결정되며, 결과는 FCM(event=
    // pairing_approved/rejected)으로 통지된다. 응답에는 요청 식별자와 만료 시각(30분)만 포함.
    @POST("api/guardian/{userId}/pair")
    suspend fun pairGuardian(
        @Path("userId") userId: String,
        @Body request: PairRequest
    ): Response<ApiResponse<PairingRequestResponse>>

    // 피보호자가 승인 요청을 승인 — 이 시점에 실제 guardian_links 관계가 생성된다.
    // 기존 관계(피보호자의 기존 보호자, 보호자의 기존 피보호자)가 있으면 자동으로 해제되고
    // 옛 파트너에게는 pairing_displaced FCM 이 발송된다.
    @POST("api/guardian/{userId}/pairing-requests/{requestId}/approve")
    suspend fun approvePairingRequest(
        @Path("userId") userId: String,
        @Path("requestId") requestId: String
    ): Response<ApiResponse<WardResponse>>

    // 피보호자가 승인 요청을 거부 — Redis 요청이 소비되고 보호자에게 pairing_rejected FCM 발송.
    @POST("api/guardian/{userId}/pairing-requests/{requestId}/reject")
    suspend fun rejectPairingRequest(
        @Path("userId") userId: String,
        @Path("requestId") requestId: String
    ): Response<ApiResponse<Unit>>

    // 보호자가 자신에게 연결된 피보호자 목록 조회 — 진입 시 페어링 모달 표시 여부 판단용.
    // 1:1 정책상 최대 1건.
    @GET("api/guardian/{userId}/wards")
    suspend fun getWards(
        @Path("userId") userId: String
    ): Response<ApiResponse<WardsWrapper>>

    // 피보호자가 자기 보호자 정보를 조회 — 감시받는 사람이 감시자를 감사할 수 있게. 없으면 null.
    @GET("api/guardian/{userId}/my-guardian")
    suspend fun getMyGuardian(
        @Path("userId") userId: String
    ): Response<ApiResponse<GuardianResponse?>>

    // 보호자·피보호자 어느 쪽에서 호출해도 관계가 해제됨. 상대방에게 pairing_unpaired FCM 발송.
    @DELETE("api/guardian/{userId}/pair/{counterpartUserId}")
    suspend fun unpair(
        @Path("userId") userId: String,
        @Path("counterpartUserId") counterpartUserId: String
    ): Response<ApiResponse<Unit>>

    // ===== Settings =====

    @GET("api/settings/notifications/{userId}")
    suspend fun getNotificationSettings(
        @Path("userId") userId: String
    ): Response<ApiResponse<NotificationSettingsResponse>>

    @PUT("api/settings/notifications/{userId}")
    suspend fun updateNotificationSettings(
        @Path("userId") userId: String,
        @Body request: NotificationSettingsRequest
    ): Response<ApiResponse<NotificationSettingsResponse>>

    @GET("api/settings/marketing/{userId}")
    suspend fun getMarketingConsent(
        @Path("userId") userId: String
    ): Response<ApiResponse<MarketingConsentResponse>>

    @PUT("api/settings/marketing/{userId}")
    suspend fun updateMarketingConsent(
        @Path("userId") userId: String,
        @Body request: MarketingConsentRequest
    ): Response<ApiResponse<MarketingConsentResponse>>

    // ===== Consents (필수 약관 재동의) =====

    // 자동 로그인 진입용 — 로그인 응답 pending_consents와 같은 목록
    @GET("api/consents/{userId}/pending")
    suspend fun getPendingConsents(
        @Path("userId") userId: String
    ): Response<ApiResponse<List<PendingConsent>>>

    // 응답은 남은 재동의 목록 — 비면 refresh로 토큰 교체해야 차단 해제
    @POST("api/consents/{userId}")
    suspend fun agreeConsents(
        @Path("userId") userId: String,
        @Body request: ConsentAgreeRequest
    ): Response<ApiResponse<List<PendingConsent>>>

    // ===== Camera (실시간 위험 지수) =====

    @GET("api/camera/score/{userId}")
    suspend fun getRiskScore(@Path("userId") userId: String): Response<ApiResponse<RiskScoreResponse>>

    @GET("api/camera/status/{userId}")
    suspend fun getRiskStatus(@Path("userId") userId: String): Response<ApiResponse<RiskStatusResponse>>

    // 피보호자 앱 heartbeat — 카메라 모드 켜져 있는 동안 2분 주기로 호출.
    // 서버는 이 호출을 근거로 6분 이상 미수신 시 오프라인 판정 후 보호자에게 통지한다.
    @POST("api/camera/heartbeat")
    suspend fun heartbeat(@Body request: HeartbeatRequest): Response<ApiResponse<Unit>>

    // ===== Live (보호자 실시간 영상) =====

    // 시청 시작·연장 겸용 — 진행 중 세션이 있으면 만료만 지금+5분으로 연장(횟수 제한·열람 기록 제외는 서버 처리)
    @POST("api/live/{elderUserId}/session")
    suspend fun startLiveSession(
        @Path("elderUserId") elderUserId: String
    ): Response<ApiResponse<LiveSessionResponse>>

    // 시청 종료 — 이미 끝난 세션이어도 성공(멱등). 서버가 LiveKit 방까지 삭제해 송출도 멈춘다
    @DELETE("api/live/{elderUserId}/session")
    suspend fun endLiveSession(
        @Path("elderUserId") elderUserId: String
    ): Response<ApiResponse<Unit>>

    // 피보호자(카메라 모드) 송출 토큰 — 토큰 사용자 본인 방, 진행 중 세션이 있을 때만.
    // 세션 없음·만료 404 LIVE_SESSION_NOT_FOUND, 동의 철회 403 LIVE_NOT_ALLOWED. 응답 형식은 시청과 같고
    // token만 송출 전용(유효시간 = 남은 세션 시간), request_delivered는 null
    @POST("api/live/me/publish-token")
    suspend fun issueLivePublishToken(): Response<ApiResponse<LiveSessionResponse>>

    // ===== Fall Logs (사고 이력) =====

    @GET("api/fall-logs/{userId}")
    suspend fun getFallLogs(
        @Path("userId") userId: String,
        @Query("level") level: String? = null
    ): Response<ApiResponse<Map<String, List<FallLogResponse>>>>

    @DELETE("api/fall-logs/{userId}/{logId}")
    suspend fun deleteFallLog(
        @Path("userId") userId: String,
        @Path("logId") logId: String
    ): Response<ApiResponse<Unit>>

    // 1시간 유효한 signed URL — 재생/다운로드 시점마다 새로 발급받아 사용
    @GET("api/fall-logs/{userId}/{logId}/video")
    suspend fun getFallLogVideo(
        @Path("userId") userId: String,
        @Path("logId") logId: String
    ): Response<ApiResponse<FallLogVideoResponse>>

    // 10분 유효한 업로드용 signed PUT URL 발급
    @POST("api/fall-logs/{userId}/{logId}/upload-url")
    suspend fun getUploadUrl(
        @Path("userId") userId: String,
        @Path("logId") logId: String
    ): Response<ApiResponse<UploadUrlResponse>>

    // signed URL로 GCS 업로드 완료 후 호출 — 서버가 GCS 객체 존재를 재확인한 뒤 video_url 반영
    @PATCH("api/fall-logs/{userId}/{logId}/video-complete")
    suspend fun completeVideoUpload(
        @Path("userId") userId: String,
        @Path("logId") logId: String
    ): Response<ApiResponse<FallLogResponse>>

    // 낙상 사고 처리(isConfirmed) — 알림 읽음(markNotificationRead)과 별개. 재알림 중단 기준
    @PATCH("api/fall-logs/{userId}/{logId}/confirm")
    suspend fun confirmFallLog(
        @Path("userId") userId: String,
        @Path("logId") logId: String
    ): Response<ApiResponse<ConfirmFallLogResponse>>

    // ===== Notifications (알림함) =====

    // 낙상·페어링·오프라인 등 전체 알림 이력, 최신순
    @GET("api/notifications/{userId}")
    suspend fun getNotifications(
        @Path("userId") userId: String
    ): Response<ApiResponse<Map<String, List<NotificationLogResponse>>>>

    // 알림 읽음(isRead) 처리
    @PATCH("api/notifications/{userId}/{notificationId}/read")
    suspend fun markNotificationRead(
        @Path("userId") userId: String,
        @Path("notificationId") notificationId: String
    ): Response<ApiResponse<NotificationLogResponse>>
}
