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
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
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
import kotlin.random.Random
import com.example.on_safe.ui.login.LoginActivity
import com.example.on_safe.util.TokenManager
import com.example.on_safe.network.dto.LandmarkPoint
import kotlinx.coroutines.launch

class CameraModeActivity : AppCompatActivity() {

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
    private lateinit var rootLayout: FrameLayout

    private var isPanelVisible = true
    private var screenSaverView: View? = null

    private var landmarkStreamClient: LandmarkStreamClient? = null
    private var poseLandmarkerHelper: PoseLandmarkerHelper? = null
    private var rollingVideoBufferManager: RollingVideoBufferManager? = null
    private val fallVideoUploader = FallVideoUploader()
    // 최신 추론 결과 캐시 — 로깅 + 위험 이벤트 중복 트리거 방지용
    private var latestFallScore: Float = 0f
    private var latestFallLevel: String = "정상"
    private var lastHandledDangerLogId: String? = null

    // 화면 보호기 타이머
    private val screenHandler = Handler(Looper.getMainLooper())
    private val inactivityRunnable = Runnable { triggerScreenSaver() }
    private val dimRestorationRunnable = Runnable { dimScreen() }   
    // 번인 방지: 화면 보호기 표시 중 주기적으로 콘텐츠 위치를 살짝 이동
    private val pixelShiftRunnable = Runnable { applyPixelShift() }

    // 화면이 어두운 상태인지 추적
    private var isScreenDimmed = false
    // 어두운 상태에서 첫 터치로 밝기가 복원된 직후 플래그 (btnWakeUp 즉시 해제 방지)
    private var justRestoredFromDim = false
    private val clearJustRestored = Runnable { justRestoredFromDim = false }
    // 카메라 provider 보관용 (화면 종료 시 해제하려고 들고 있음)
    private var cameraProvider: ProcessCameraProvider? = null
    // startCamera()에서 바인딩한 프리뷰 — 촬영 시작/종료 시 ImageAnalysis를 붙였다 뗐다 하며 재바인딩할 때 재사용
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var videoCapture: VideoCapture<Recorder>? = null

    companion object {
        private const val INACTIVITY_TIMEOUT_MS  = 10 * 60 * 1000L
        private const val BRIGHTNESS_RESTORE_MS  = 10_000L  // 터치 후 밝기 유지 시간
        private const val BRIGHTNESS_DIM         = 0.01f
        private const val BRIGHTNESS_SYSTEM      = -1f
        private const val PIXEL_SHIFT_INTERVAL_MS = 60_000L  // 번인 방지: 60초마다 위치 이동
        private const val PIXEL_SHIFT_RANGE_PX    = 5        // ±5px 범위에서 랜덤 이동
    }

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

        bindViews()
        setState(CameraState.STANDBY)

        // 권한이 있으면 바로 카메라 켜고, 없으면 권한 요청
        if (areCameraPermissionsGranted()) {
            startCamera()
        } else {
            requestCameraPermissions.launch(cameraPermissions)
        }

        setupClickListeners()
        resetInactivityTimer()

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
        resetInactivityTimer()
    }

    override fun onPause() {
        super.onPause()
        screenHandler.removeCallbacks(inactivityRunnable)
        screenHandler.removeCallbacks(dimRestorationRunnable)
        restoreBrightness()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraProvider?.unbindAll()     //화면 종료 시 카메라 해제
        screenHandler.removeCallbacksAndMessages(null)
        justRestoredFromDim = false
        if (currentState == CameraState.STREAMING || currentState == CameraState.CONNECTING) {
            poseLandmarkerHelper?.stop()
            landmarkStreamClient?.close()
            rollingVideoBufferManager?.stop()
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (screenSaverView != null) {
            if (isScreenDimmed) {
                // 화면 어두울 때 첫 터치 → 밝기만 복원, 보호기 유지
                // justRestoredFromDim 플래그를 켜서 같은 터치의 btnWakeUp 클릭이 해제 동작 못 하게 막음
                justRestoredFromDim = true
                screenHandler.removeCallbacks(clearJustRestored)
                screenHandler.postDelayed(clearJustRestored, 300)
            }
            restoreBrightnessTemporarily()
        } else {
            resetInactivityTimer()
        }
    }

    private fun bindViews() {
        rootLayout              = findViewById(android.R.id.content)
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

        // TODO: API에서 보호자 이름, 기기 ID 받아서 tvGuardianName / tvDeviceId에 설정
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

    private fun startRecording() {
        setState(CameraState.CONNECTING)

        val userId = TokenManager.getUserId(this)
        val accessToken = TokenManager.getAccessToken(this)
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        if (accessToken.isNullOrBlank() || userId.isBlank()) {
            Toast.makeText(this, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            setState(CameraState.FAILED)
            return
        }

        landmarkStreamClient = LandmarkStreamClient(object : LandmarkStreamClient.Listener {
            override fun onInitOk() {
                runOnUiThread {
                    poseLandmarkerHelper = PoseLandmarkerHelper(
                        this@CameraModeActivity,
                        object : PoseLandmarkerHelper.Listener {
                            override fun onLandmarks(
                                frameIndex: Int,
                                timestampSec: Float,
                                landmarks: List<LandmarkPoint>
                            ) {
                                landmarkStreamClient?.sendFrame(frameIndex, timestampSec, landmarks)
                            }

                            override fun onError(message: String) {
                                Log.w("CameraModeActivity", "PoseLandmarkerHelper 오류: $message")
                            }
                        }
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
                Log.d("CameraModeActivity", "낙상 추론 결과: score=$fallScore fall=$fall level=$level logId=$logId")

                if (level == "위험" && logId != null && logId != lastHandledDangerLogId) {
                    lastHandledDangerLogId = logId
                    val ownerUserId = TokenManager.getUserId(this@CameraModeActivity)
                    rollingVideoBufferManager?.captureDangerClip(
                        logId = logId,
                        onReady = { clipFile ->
                            lifecycleScope.launch { fallVideoUploader.upload(ownerUserId, logId, clipFile) }
                        },
                        onError = { e ->
                            Log.w("CameraModeActivity", "위험 이벤트 클립 합성 실패 (logId=$logId)", e)
                        }
                    )
                }
            }

            override fun onFailure(t: Throwable) {
                runOnUiThread {
                    Log.w("CameraModeActivity", "WS 연결 실패", t)
                    Toast.makeText(this@CameraModeActivity, "서버 연결에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    setState(CameraState.FAILED)
                }
            }

            override fun onClosed() {
                Log.d("CameraModeActivity", "WS 연결 종료")
            }
        })
        landmarkStreamClient?.connect(userId, deviceId, accessToken)
    }

    private fun startCamera() {
        // 카메라 provider 비동기로 가져오기
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            cameraProvider = provider

            // 프리뷰를 만들어서 화면(previewView)에 연결
            val previewUseCase = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            preview = previewUseCase

            try {
                provider.unbindAll()  // 기존 연결 정리
                // 후면 카메라 + 프리뷰를 이 화면 생명주기에 바인딩
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    previewUseCase
                )
            } catch (e: Exception) {
                // 바인딩 실패 시 사용자에게 알림
                Toast.makeText(this, "카메라를 시작할 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))  // 메인 스레드에서 실행
    }

    // 촬영 시작: 이미 켜져 있는 previewUseCase 위에 ImageAnalysis + VideoCapture 추가 바인딩 (프리뷰 재생성 없음)
    private fun bindStreamingUseCases(helper: PoseLandmarkerHelper, bufferManager: RollingVideoBufferManager) {
        val provider = cameraProvider ?: return
        val previewUseCase = preview ?: return

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
                previewUseCase,
                analysisUseCase,
                videoCaptureUseCase
            )
        } catch (e: Exception) {
            Log.w("CameraModeActivity", "ImageAnalysis/VideoCapture 바인딩 실패", e)
        }
    }

    // 촬영 종료: ImageAnalysis/VideoCapture만 떼고 previewUseCase만 다시 바인딩 (프리뷰는 계속 유지)
    private fun unbindStreamingUseCases() {
        val provider = cameraProvider ?: return
        val previewUseCase = preview ?: return
        imageAnalysis = null
        videoCapture = null

        try {
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, previewUseCase)
        } catch (e: Exception) {
            Log.w("CameraModeActivity", "프리뷰 재바인딩 실패", e)
        }
    }

    private fun showStopRecordingDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_stop_recording)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val maxWidth = resources.getDimensionPixelSize(R.dimen.logout_dialog_max_width)
            setLayout(maxWidth, WindowManager.LayoutParams.WRAP_CONTENT)
        }
        dialog.setCanceledOnTouchOutside(false)
        dialog.findViewById<TextView>(R.id.btnStopCancel).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<TextView>(R.id.btnStopConfirm).setOnClickListener {
            dialog.dismiss()
            stopRecording()
        }
        dialog.show()
    }

    private fun stopRecording() {
        poseLandmarkerHelper?.stop()
        poseLandmarkerHelper = null
        landmarkStreamClient?.close()
        landmarkStreamClient = null
        rollingVideoBufferManager?.stop()
        rollingVideoBufferManager = null
        lastHandledDangerLogId = null
        unbindStreamingUseCases()
        setState(CameraState.STANDBY)
    }

    fun setState(state: CameraState) {
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

    private fun showLogoutDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_logout)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // 가로 화면에서 모달이 너무 넓어지지 않도록 최대 너비 제한
            val maxWidth = resources.getDimensionPixelSize(R.dimen.logout_dialog_max_width)
            setLayout(maxWidth, WindowManager.LayoutParams.WRAP_CONTENT)
        }
        dialog.setCanceledOnTouchOutside(false)
        dialog.findViewById<TextView>(R.id.btnLogoutCancel).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<TextView>(R.id.btnLogoutConfirm).setOnClickListener {
            dialog.dismiss()
            handleLogout()
        }
        dialog.show()
    }

    private fun handleLogout() {
        // TODO: 서버 로그아웃 API 호출 (POST /auth/logout)
        TokenManager.clear(this)
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }

    fun showScreenSaver() {
        if (screenSaverView != null) return
        val overlay = LayoutInflater.from(this).inflate(R.layout.activity_screen_saver, rootLayout, false)
        overlay.findViewById<View>(R.id.btnWakeUp).setOnClickListener { hideScreenSaver() }
        rootLayout.addView(overlay)
        screenSaverView = overlay
        // 번인 방지: 60초마다 콘텐츠 위치를 ±5px 범위에서 랜덤 이동
        screenHandler.postDelayed(pixelShiftRunnable, PIXEL_SHIFT_INTERVAL_MS)
    }

    // 번인 방지 — 화면 보호기 뷰를 살짝 랜덤 이동시키고 다음 이동을 다시 예약
    private fun applyPixelShift() {
        val view = screenSaverView ?: return
        view.translationX = Random.nextInt(-PIXEL_SHIFT_RANGE_PX, PIXEL_SHIFT_RANGE_PX + 1).toFloat()
        view.translationY = Random.nextInt(-PIXEL_SHIFT_RANGE_PX, PIXEL_SHIFT_RANGE_PX + 1).toFloat()
        screenHandler.postDelayed(pixelShiftRunnable, PIXEL_SHIFT_INTERVAL_MS)
    }

    fun hideScreenSaver() {
        // 화면이 어두운 상태에서의 첫 터치로 밝기가 방금 복원된 경우 → 해제 차단
        if (justRestoredFromDim) return
        screenSaverView?.let {
            rootLayout.removeView(it)
            screenSaverView = null
        }
        screenHandler.removeCallbacks(dimRestorationRunnable)
        screenHandler.removeCallbacks(clearJustRestored)
        screenHandler.removeCallbacks(pixelShiftRunnable)
        restoreBrightness()
        resetInactivityTimer()
    }

    private fun resetInactivityTimer() {
        screenHandler.removeCallbacks(inactivityRunnable)
        screenHandler.postDelayed(inactivityRunnable, INACTIVITY_TIMEOUT_MS)
    }

    private fun triggerScreenSaver() {
        dimScreen()
        showScreenSaver()
    }

    private fun dimScreen() {
        isScreenDimmed = true
        window.attributes = window.attributes.also { it.screenBrightness = BRIGHTNESS_DIM }
    }

    private fun restoreBrightness() {
        isScreenDimmed = false
        screenHandler.removeCallbacks(dimRestorationRunnable)
        window.attributes = window.attributes.also { it.screenBrightness = BRIGHTNESS_SYSTEM }
    }

    // 터치 시 밝기 10초간 복원 후 다시 어둡게 (화면 보호기 유지)
    private fun restoreBrightnessTemporarily() {
        isScreenDimmed = false
        screenHandler.removeCallbacks(dimRestorationRunnable)
        window.attributes = window.attributes.also { it.screenBrightness = BRIGHTNESS_SYSTEM }
        screenHandler.postDelayed(dimRestorationRunnable, BRIGHTNESS_RESTORE_MS)
    }

    private fun areCameraPermissionsGranted(): Boolean =
        cameraPermissions.all { perm ->
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }

    // 권한 완전 거부 ('다시 묻지 않음') → 설정 화면으로 안내
    private fun showPermissionSettingsDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_permission_settings)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val maxWidth = resources.getDimensionPixelSize(R.dimen.logout_dialog_max_width)
            setLayout(maxWidth, WindowManager.LayoutParams.WRAP_CONTENT)
        }
        dialog.setCanceledOnTouchOutside(false)

        dialog.findViewById<TextView>(R.id.tvPermDialogMessage).text =
            "카메라·마이크 권한이 '다시 묻지 않음'으로\n거부되었습니다. 앱 설정에서 직접 허용해주세요."

        dialog.findViewById<TextView>(R.id.btnPermDialogCancel).setOnClickListener {
            dialog.dismiss()
            finish()
        }
        dialog.findViewById<TextView>(R.id.btnPermDialogConfirm).setOnClickListener {
            dialog.dismiss()
            openSettings.launch(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            )
        }
        dialog.show()
    }

    // 권한 일시 거부 → 재요청 안내 다이얼로그
    private fun showPermissionRationaleDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_permission_settings)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val maxWidth = resources.getDimensionPixelSize(R.dimen.logout_dialog_max_width)
            setLayout(maxWidth, WindowManager.LayoutParams.WRAP_CONTENT)
        }
        dialog.setCanceledOnTouchOutside(false)

        dialog.findViewById<TextView>(R.id.tvPermDialogMessage).text =
            "카메라·마이크 권한이 필요합니다.\n권한을 허용해야 카메라 모드를 사용할 수 있습니다."

        dialog.findViewById<TextView>(R.id.btnPermDialogCancel).setOnClickListener {
            dialog.dismiss()
            finish()
        }
        dialog.findViewById<TextView>(R.id.btnPermDialogConfirm).setOnClickListener {
            dialog.dismiss()
            requestCameraPermissions.launch(cameraPermissions)
        }
        dialog.show()
    }
}
