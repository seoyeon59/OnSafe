package com.example.on_safe.ui.camera

import android.Manifest
import android.app.AlertDialog
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.on_safe.R
import com.example.on_safe.ui.login.LoginActivity

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
    private lateinit var rootLayout: FrameLayout

    private var isPanelVisible = true
    private var screenSaverView: View? = null

    // 화면 보호기 타이머
    private val screenHandler = Handler(Looper.getMainLooper())
    private val inactivityRunnable = Runnable { triggerScreenSaver() }
    private val dimRestorationRunnable = Runnable { dimScreen() }

    companion object {
        private const val INACTIVITY_TIMEOUT_MS = 10 * 60 * 1000L
        private const val BRIGHTNESS_RESTORE_MS  = 5_000L
        private const val BRIGHTNESS_DIM         = 0.01f
        private const val BRIGHTNESS_SYSTEM      = -1f
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
                    Toast.makeText(this, "카메라·마이크 권한이 필요합니다.", Toast.LENGTH_LONG).show()
                    finish()
                }
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

        if (!areCameraPermissionsGranted()) {
            requestCameraPermissions.launch(cameraPermissions)
        }

        setupClickListeners()
        resetInactivityTimer()
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
        screenHandler.removeCallbacksAndMessages(null)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (screenSaverView != null) {
            // 화면 보호기 표시 중 터치 → 5초간 밝기 복원, 보호기는 유지
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

        layoutStatusBadge.background = statusBadgeBg

        // TODO: API에서 보호자 이름, 기기 ID 받아서 tvGuardianName / tvDeviceId에 설정
    }

    private fun setupClickListeners() {
        btnToggleRecording.setOnClickListener {
            when (currentState) {
                CameraState.STANDBY, CameraState.FAILED -> startRecording()
                CameraState.STREAMING -> stopRecording()
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

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (!isPanelVisible) toggleFullscreen() else super.onBackPressed()
    }

    private fun startRecording() {
        setState(CameraState.CONNECTING)
        // TODO: CameraX 카메라 시작 + 서버 스트리밍 연결 로직으로 교체
        Handler(Looper.getMainLooper()).postDelayed({ setState(CameraState.STREAMING) }, 2000)
    }

    private fun stopRecording() {
        // TODO: CameraX 중단 + 서버 전송 종료
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
    }

    fun hideScreenSaver() {
        screenSaverView?.let {
            rootLayout.removeView(it)
            screenSaverView = null
        }
        screenHandler.removeCallbacks(dimRestorationRunnable)
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
        window.attributes = window.attributes.also { it.screenBrightness = BRIGHTNESS_DIM }
    }

    private fun restoreBrightness() {
        screenHandler.removeCallbacks(dimRestorationRunnable)
        window.attributes = window.attributes.also { it.screenBrightness = BRIGHTNESS_SYSTEM }
    }

    // 5초간 밝기 복원 후 다시 어둡게 (화면 보호기 유지 중 터치 시)
    private fun restoreBrightnessTemporarily() {
        screenHandler.removeCallbacks(dimRestorationRunnable)
        window.attributes = window.attributes.also { it.screenBrightness = BRIGHTNESS_SYSTEM }
        screenHandler.postDelayed(dimRestorationRunnable, BRIGHTNESS_RESTORE_MS)
    }

    private fun areCameraPermissionsGranted(): Boolean =
        cameraPermissions.all { perm ->
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }

    private fun showPermissionSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("권한 설정 필요")
            .setMessage("카메라·마이크 권한이 '다시 묻지 않음'으로 거부되었습니다.\n앱 설정에서 직접 허용해주세요.")
            .setPositiveButton("설정으로 이동") { _, _ ->
                openSettings.launch(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                )
            }
            .setNegativeButton("취소") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }
}
