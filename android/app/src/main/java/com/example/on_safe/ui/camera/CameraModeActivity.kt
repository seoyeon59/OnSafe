package com.example.on_safe.ui.camera

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.lifecycle.lifecycleScope
import com.example.on_safe.R
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.LandmarkPoint
import com.example.on_safe.ui.login.LoginActivity
import com.example.on_safe.util.TokenManager
import kotlinx.coroutines.launch

private const val TAG = "CameraModeActivity"

class CameraModeActivity : AppCompatActivity() {

    private val viewModel: CameraModeViewModel by viewModels()

    // 이 기기의 식별자 — 낙상 로그/랜드마크 스트림에도 그대로 쓰이는 값이라 한 곳에서만 계산해 공유한다
    private val deviceId: String by lazy {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }

    private lateinit var previewView: androidx.camera.view.PreviewView
    private lateinit var layoutStandby: LinearLayout
    private lateinit var layoutLiveBadge: LinearLayout
    private lateinit var layoutStatusBadge: LinearLayout
    private lateinit var viewStatusDot: View
    private lateinit var tvStatusText: TextView
    private lateinit var layoutStandbyContent: LinearLayout
    private lateinit var layoutConnectingContent: LinearLayout
    private lateinit var tvGuardianName: TextView
    private lateinit var tvDeviceId: TextView
    private lateinit var btnToggleRecording: LinearLayout
    private lateinit var tvToggleText: TextView
    private lateinit var ivToggleIcon: ImageView
    private lateinit var btnLogout: LinearLayout
    private lateinit var sidePanel: LinearLayout
    private lateinit var btnFullscreen: FrameLayout
    private lateinit var btnHamburger: ImageButton
    private lateinit var btnTutorial: ImageView

    // 화면보호기/번인방지/자동 dim — 카메라 로직과 독립적인 관심사라 별도 클래스로 분리
    private lateinit var screenSaverController: ScreenSaverController

    private var isPanelVisible = true

    private var landmarkStreamClient: LandmarkStreamClient? = null
    private var poseLandmarkerHelper: PoseLandmarkerHelper? = null
    private var rollingVideoBufferManager: RollingVideoBufferManager? = null
    private val fallVideoUploader = FallVideoUploader()
    // 최신 추론 결과 캐시 — 로깅 + 위험 이벤트 중복 트리거 방지용
    private var latestFallScore: Float = 0f
    private var latestFallLevel: String = "정상"
    private var lastHandledDangerLogId: String? = null

    // 카메라 provider 보관용 (화면 종료 시 해제하려고 들고 있음)
    private var cameraProvider: ProcessCameraProvider? = null
    // startCamera()에서 바인딩한 프리뷰 — 촬영 시작/종료 시 ImageAnalysis를 붙였다 뗐다 하며 재바인딩할 때 재사용
    private var previewUseCase: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var videoCapture: VideoCapture<Recorder>? = null

    enum class CameraState { STANDBY, CONNECTING, STREAMING, FAILED }
    private var currentState = CameraState.STANDBY

    // setColor()만 호출해도 cornerRadius가 유지되도록 인스턴스 재사용
    private val statusBadgeBg = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = Float.MAX_VALUE
    }

    private val cameraPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private val requestCameraPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val deniedAny = results.values.any { !it }
            if (deniedAny) {
                val permanentlyDenied = results.entries
                    .filter { !it.value }
                    .any { !shouldShowRequestPermissionRationale(it.key) }
                if (permanentlyDenied) showPermissionSettingsDialog()
                else {
                    // 일시 거부 → 안내 다이얼로그로 재요청 기회 제공 (즉시 종료 대신 자연스러운 흐름으로 연결)
                    showPermissionRationaleDialog()
                }
            } else {
                // 전부 허용됨 -> 카메라 시작
                startCamera()
            }
        }

    private val openSettings =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (!areCameraPermissionsGranted()) finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_mode)

        // 휴대폰 시스템 화면 자동 꺼짐을 막고, 앱 자체 화면 보호기(dim + 오버레이)가 절전을 담당하게 함
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        bindViews()
        setState(CameraState.STANDBY)
        observeViewModel()
        viewModel.setDeviceId(deviceId)
        viewModel.loadGuardianName(TokenManager.getUserId(this))

        // 권한이 있으면 바로 카메라 켜고, 없으면 권한 요청
        if (areCameraPermissionsGranted()) {
            startCamera()
        } else {
            requestCameraPermissions.launch(cameraPermissions)
        }

        setupClickListeners()
        screenSaverController.resetInactivityTimer()

        // 패널이 닫혀 있으면 뒤로가기로 패널 복귀, 열려 있으면 기본 동작(finish)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isPanelVisible) {
                    toggleFullscreen()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        screenSaverController.resetInactivityTimer()
    }

    override fun onPause() {
        super.onPause()
        screenSaverController.pauseTimers()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraProvider?.unbindAll()     //화면 종료 시 카메라 해제
        screenSaverController.release()
        // 상태와 무관하게 항상 정리 — FAILED 상태(WS 끊김 등)에서도 리소스가 살아있을 수 있어
        // STREAMING/CONNECTING일 때만 정리하면 새는 경우가 있었음
        poseLandmarkerHelper?.stop()
        landmarkStreamClient?.close()
        rollingVideoBufferManager?.stop()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        screenSaverController.onUserInteraction()
    }

    private fun bindViews() {
        val rootLayout = findViewById<FrameLayout>(android.R.id.content)
        screenSaverController = ScreenSaverController(window, rootLayout)

        previewView             = findViewById(R.id.previewView)
        layoutStandby           = findViewById(R.id.layoutStandby)
        layoutLiveBadge         = findViewById(R.id.layoutLiveBadge)
        layoutStatusBadge       = findViewById(R.id.layoutStatusBadge)
        viewStatusDot           = findViewById(R.id.viewStatusDot)
        tvStatusText            = findViewById(R.id.tvStatusText)
        layoutStandbyContent    = findViewById(R.id.layoutStandbyContent)
        layoutConnectingContent = findViewById(R.id.layoutConnectingContent)
        tvGuardianName          = findViewById(R.id.tvGuardianName)
        tvDeviceId              = findViewById(R.id.tvDeviceId)
        btnToggleRecording      = findViewById(R.id.btnToggleRecording)
        tvToggleText            = findViewById(R.id.tvToggleText)
        ivToggleIcon            = findViewById(R.id.ivToggleIcon)
        btnLogout               = findViewById(R.id.btnLogout)
        sidePanel               = findViewById(R.id.sidePanel)
        btnFullscreen           = findViewById(R.id.btnFullscreen)
        btnHamburger            = findViewById(R.id.btnHamburger)
        btnTutorial             = findViewById(R.id.btnTutorial)

        layoutStatusBadge.background = statusBadgeBg
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            if (state.guardianName.isNotBlank()) tvGuardianName.text = state.guardianName
            if (state.deviceId.isNotBlank()) tvDeviceId.text = state.deviceId
        }
    }

    private fun setupClickListeners() {
        btnToggleRecording.setOnClickListener {
            when (currentState) {
                CameraState.STANDBY, CameraState.FAILED -> startRecording()
                CameraState.STREAMING -> showStopRecordingDialog()
                CameraState.CONNECTING -> { /* 연결 중 무시 */ }
            }
        }

        btnLogout.setOnClickListener {
            if (currentState == CameraState.STREAMING || currentState == CameraState.CONNECTING) {
                Toast.makeText(this, "촬영 종료 후 로그아웃해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showLogoutDialog()
        }

        btnFullscreen.setOnClickListener { toggleFullscreen() }
        btnHamburger.setOnClickListener { toggleFullscreen() }
        btnTutorial.setOnClickListener {
            startActivity(
                com.example.on_safe.ui.tutorial.TutorialActivity.intentFromSettings(this)
            )
        }
    }

    private fun toggleFullscreen() {
        if (isPanelVisible) {
            sidePanel.animate()
                .translationX(sidePanel.width.toFloat())
                .setDuration(240)
                .withEndAction {
                    sidePanel.visibility = View.GONE
                    sidePanel.translationX = 0f
                }
                .start()
            btnFullscreen.visibility = View.GONE
            btnHamburger.visibility = View.VISIBLE
            isPanelVisible = false
        } else {
            sidePanel.translationX = resources.getDimension(R.dimen.side_panel_width)
            sidePanel.visibility = View.VISIBLE
            sidePanel.animate().translationX(0f).setDuration(240).start()
            btnFullscreen.visibility = View.VISIBLE
            btnHamburger.visibility = View.GONE
            isPanelVisible = true
        }
    }

    // ──────────────────────────────────────────────────────────
    // 촬영 시작/종료 — WS 연결 + landmark 추론 + 위험 클립 업로드 오케스트레이션
    // ──────────────────────────────────────────────────────────

    private fun startRecording() {
        setState(CameraState.CONNECTING)

        val userId = TokenManager.getUserId(this)
        val accessToken = TokenManager.getAccessToken(this)

        if (accessToken.isNullOrBlank() || userId.isBlank()) {
            Toast.makeText(this, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            setState(CameraState.FAILED)
            return
        }

        landmarkStreamClient = LandmarkStreamClient(createStreamListener())
        landmarkStreamClient?.connect(userId, deviceId, accessToken)
    }

    // WS 연결 생명주기 콜백 — startRecording()에서 분리해 "무엇을 트리거하는지"와
    // "연결이 시작/진행/종료될 때 무엇을 하는지"를 나눴다 (내용은 원본과 동일, 위치만 이동)
    private fun createStreamListener(): LandmarkStreamClient.Listener =
        object : LandmarkStreamClient.Listener {
            override fun onInitOk() {
                runOnUiThread {
                    poseLandmarkerHelper = PoseLandmarkerHelper(
                        this@CameraModeActivity,
                        createPoseListener()
                    )
                    val helper = poseLandmarkerHelper ?: return@runOnUiThread
                    helper.start()
                    val bufferManager = RollingVideoBufferManager(this@CameraModeActivity)
                    rollingVideoBufferManager = bufferManager
                    bindStreamingUseCases(helper, bufferManager)
                    bufferManager.start()
                    setState(CameraState.STREAMING)
                }
            }

            override fun onResult(fallScore: Float, fall: Boolean, level: String, logId: String?) {
                latestFallScore = fallScore
                latestFallLevel = level
                Log.d(TAG, "낙상 추론 결과: score=$fallScore fall=$fall level=$level logId=$logId")

                if (level == "위험" && logId != null && logId != lastHandledDangerLogId) {
                    lastHandledDangerLogId = logId
                    val ownerUserId = TokenManager.getUserId(this@CameraModeActivity)
                    rollingVideoBufferManager?.captureDangerClip(
                        logId = logId,
                        onReady = { clipFile ->
                            lifecycleScope.launch { fallVideoUploader.upload(ownerUserId, logId, clipFile) }
                        },
                        onError = { e ->
                            Log.w(TAG, "위험 이벤트 클립 합성 실패 (logId=$logId)", e)
                        }
                    )
                }
            }

            override fun onFailure(t: Throwable) {
                runOnUiThread {
                    Log.w(TAG, "WS 연결 실패", t)
                    Toast.makeText(this@CameraModeActivity, "서버 연결에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    setState(CameraState.FAILED)
                }
            }

            override fun onClosed() {
                Log.d(TAG, "WS 연결 종료")
            }
        }

    // MediaPipe pose landmark 콜백 — 프레임을 그대로 WS로 중계
    private fun createPoseListener(): PoseLandmarkerHelper.Listener =
        object : PoseLandmarkerHelper.Listener {
            override fun onLandmarks(
                frameIndex: Int,
                timestampSec: Float,
                landmarks: List<LandmarkPoint>
            ) {
                landmarkStreamClient?.sendFrame(frameIndex, timestampSec, landmarks)
            }

            override fun onError(message: String) {
                Log.w(TAG, "PoseLandmarkerHelper 오류: $message")
            }
        }

    private fun startCamera() {
        // 카메라 provider 비동기로 가져오기
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            cameraProvider = provider

            // 프리뷰를 만들어서 화면(previewView)에 연결
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            previewUseCase = preview

            try {
                provider.unbindAll()  // 기존 연결 정리
                // 후면 카메라 + 프리뷰를 이 화면 생명주기에 바인딩
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview
                )
            } catch (e: Exception) {
                // 바인딩 실패 시 사용자에게 알림
                Log.w(TAG, "카메라 바인딩 실패", e)
                Toast.makeText(this, "카메라를 시작할 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))  // 메인 스레드에서 실행
    }

    // 촬영 시작: 이미 켜져 있는 previewUseCase 위에 ImageAnalysis + VideoCapture 추가 바인딩 (프리뷰 재생성 없음)
    private fun bindStreamingUseCases(helper: PoseLandmarkerHelper, bufferManager: RollingVideoBufferManager) {
        val provider = cameraProvider ?: return
        val preview = previewUseCase ?: return

        val analysisUseCase = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { it.setAnalyzer(helper.executor, helper::analyze) }
        imageAnalysis = analysisUseCase

        val videoCaptureUseCase = bufferManager.videoCapture
        videoCapture = videoCaptureUseCase

        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysisUseCase,
                videoCaptureUseCase
            )
        } catch (e: Exception) {
            Log.w(TAG, "ImageAnalysis/VideoCapture 바인딩 실패", e)
        }
    }

    // 촬영 종료: ImageAnalysis/VideoCapture만 떼고 previewUseCase만 다시 바인딩 (프리뷰는 계속 유지)
    private fun unbindStreamingUseCases() {
        val provider = cameraProvider ?: return
        val preview = previewUseCase ?: return
        imageAnalysis = null
        videoCapture = null

        try {
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview)
        } catch (e: Exception) {
            Log.w(TAG, "프리뷰 재바인딩 실패", e)
        }
    }

    private fun stopRecording() {
        // 카메라가 프레임/녹화 이벤트를 더 이상 만들지 않도록 먼저 unbind한 뒤에
        // helper/버퍼 매니저의 executor를 정리한다 (onDestroy()와 동일한 순서).
        // 반대로 하면 unbind가 실제로 반영되기 전 CameraX가 이미 종료된 executor로
        // 이벤트를 넘기려다 RejectedExecutionException을 던질 여지가 커진다.
        unbindStreamingUseCases()
        poseLandmarkerHelper?.stop()
        poseLandmarkerHelper = null
        landmarkStreamClient?.close()
        landmarkStreamClient = null
        rollingVideoBufferManager?.stop()
        rollingVideoBufferManager = null
        lastHandledDangerLogId = null
        setState(CameraState.STANDBY)
    }

    private fun setState(state: CameraState) {
        currentState = state
        when (state) {
            CameraState.STANDBY -> {
                layoutStandby.visibility = View.VISIBLE
                layoutStandbyContent.visibility = View.VISIBLE
                layoutConnectingContent.visibility = View.GONE
                layoutLiveBadge.visibility = View.GONE
                setStatusBadge("대기 중", Color.parseColor("#9FA6AC"))
                setToggleButton("촬영 시작하기", R.drawable.ic_camera, Color.parseColor("#4D80FF"))
                btnToggleRecording.isEnabled = true
                btnToggleRecording.alpha = 1.0f
            }
            CameraState.CONNECTING -> {
                layoutStandby.visibility = View.VISIBLE
                layoutStandbyContent.visibility = View.GONE        // 대기 아이콘 숨김
                layoutConnectingContent.visibility = View.VISIBLE  // 스피너 + "연결 중..." 표시
                layoutLiveBadge.visibility = View.GONE
                setStatusBadge("연결 중...", Color.parseColor("#F59E0B"))
                setToggleButton("연결 중...", R.drawable.ic_camera, Color.parseColor("#4D80FF"))
                btnToggleRecording.isEnabled = false
                btnToggleRecording.alpha = 0.4f
            }
            CameraState.STREAMING -> {
                layoutStandby.visibility = View.GONE
                layoutStandbyContent.visibility = View.VISIBLE     // 다음 STANDBY 상태 대비 초기화
                layoutConnectingContent.visibility = View.GONE
                layoutLiveBadge.visibility = View.VISIBLE
                setStatusBadge("전송 중", Color.parseColor("#22C55E"))
                setToggleButton("촬영 종료하기", R.drawable.ic_stop, Color.parseColor("#EF4444"))
                btnToggleRecording.isEnabled = true
                btnToggleRecording.alpha = 1.0f
            }
            CameraState.FAILED -> {
                layoutStandby.visibility = View.VISIBLE
                layoutStandbyContent.visibility = View.VISIBLE
                layoutConnectingContent.visibility = View.GONE
                layoutLiveBadge.visibility = View.GONE
                setStatusBadge("연결 실패", Color.parseColor("#EF4444"))
                setToggleButton("다시 시도하기", R.drawable.ic_camera, Color.parseColor("#4D80FF"))
                btnToggleRecording.isEnabled = true
                btnToggleRecording.alpha = 1.0f
            }
        }
    }

    // setColor()만으로 pill 모양 유지 (cornerRadius 덮어쓰지 않음)
    private fun setStatusBadge(text: String, color: Int) {
        val bgColor = (color and 0x00FFFFFF) or (0x26 shl 24) // 알파 15%
        statusBadgeBg.setColor(bgColor)
        tvStatusText.text = text
        tvStatusText.setTextColor(color)
        viewStatusDot.background.mutate().setTint(color)
    }

    private fun setToggleButton(text: String, iconRes: Int, bgColor: Int) {
        tvToggleText.text = text
        ivToggleIcon.setImageResource(iconRes)
        btnToggleRecording.backgroundTintList =
            android.content.res.ColorStateList.valueOf(bgColor)
    }

    // 서버 로그아웃(리프레시 토큰 블랙리스트) → 로컬 토큰 정리 → 로그인 화면 이동.
    // 서버 호출이 실패해도 로컬 로그아웃은 진행 — 사용자 관점에서 항상 성공해야 함.
    private fun handleLogout() {
        lifecycleScope.launch {
            try {
                ApiClient.api.logout(TokenManager.getRefreshToken(this@CameraModeActivity))
            } catch (_: Exception) {
                // 무시하고 로컬 정리로 진행
            }
            TokenManager.clear(this@CameraModeActivity)
            startActivity(Intent(this@CameraModeActivity, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }

    private fun areCameraPermissionsGranted(): Boolean =
        cameraPermissions.all { perm ->
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }

    // ──────────────────────────────────────────────────────────
    // 다이얼로그 — 원본에서 4개 메서드가 레이아웃/취소·확인 버튼 세팅 로직을 거의 동일하게
    // 반복하고 있어서, 공통 뼈대만 showConfirmDialog()/showPermissionDialog()로 뽑아냈다.
    // 각 다이얼로그가 실제로 무엇을 확인/거절하는지(비즈니스 로직)는 전혀 바꾸지 않았다.
    // ──────────────────────────────────────────────────────────

    private fun showStopRecordingDialog() {
        showConfirmDialog(
            layoutRes = R.layout.dialog_stop_recording,
            cancelId = R.id.btnStopCancel,
            confirmId = R.id.btnStopConfirm,
            onConfirm = ::stopRecording
        )
    }

    private fun showLogoutDialog() {
        showConfirmDialog(
            layoutRes = R.layout.dialog_logout,
            cancelId = R.id.btnLogoutCancel,
            confirmId = R.id.btnLogoutConfirm,
            onConfirm = ::handleLogout
        )
    }

    // 권한 완전 거부 ('다시 묻지 않음') → 설정 화면으로 안내
    private fun showPermissionSettingsDialog() {
        showPermissionDialog(
            message = "카메라·마이크 권한이 '다시 묻지 않음'으로\n거부되었습니다. 앱 설정에서 직접 허용해주세요."
        ) {
            openSettings.launch(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            )
        }
    }

    // 권한 일시 거부 → 재요청 안내 다이얼로그
    private fun showPermissionRationaleDialog() {
        showPermissionDialog(
            message = "카메라·마이크 권한이 필요합니다.\n권한을 허용해야 카메라 모드를 사용할 수 있습니다."
        ) {
            requestCameraPermissions.launch(cameraPermissions)
        }
    }

    // 취소/확인 버튼 두 개짜리 단순 확인 다이얼로그 공통 뼈대
    // (dialog_stop_recording, dialog_logout 레이아웃이 대상 — 둘 다 원본에서 이미 같은
    // maxWidth 치수(logout_dialog_max_width)를 공유하고 있어 합쳐도 동작 차이가 없다)
    private fun showConfirmDialog(
        @LayoutRes layoutRes: Int,
        @IdRes cancelId: Int,
        @IdRes confirmId: Int,
        onConfirm: () -> Unit
    ) {
        val dialog = buildBaseDialog(layoutRes)
        dialog.findViewById<TextView>(cancelId).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<TextView>(confirmId).setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }
        dialog.show()
    }

    // 권한 안내 다이얼로그 공통 뼈대 — 취소 시 항상 finish()하는 것까지 두 원본 메서드에서 동일했다
    private fun showPermissionDialog(message: String, onConfirm: () -> Unit) {
        val dialog = buildBaseDialog(R.layout.dialog_permission_settings)
        dialog.findViewById<TextView>(R.id.tvPermDialogMessage).text = message
        dialog.findViewById<TextView>(R.id.btnPermDialogCancel).setOnClickListener {
            dialog.dismiss()
            finish()
        }
        dialog.findViewById<TextView>(R.id.btnPermDialogConfirm).setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }
        dialog.show()
    }

    // 투명 배경 + 제목 없음 + 화면 폭에 맞춘 최대 너비 + 바깥 터치로 안 닫힘 — 4개 다이얼로그 공통 설정
    private fun buildBaseDialog(@LayoutRes layoutRes: Int): Dialog {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(layoutRes)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val maxWidth = resources.getDimensionPixelSize(R.dimen.logout_dialog_max_width)
            setLayout(maxWidth, WindowManager.LayoutParams.WRAP_CONTENT)
        }
        dialog.setCanceledOnTouchOutside(false)
        return dialog
    }
}
