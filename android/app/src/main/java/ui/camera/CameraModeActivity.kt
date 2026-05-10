package com.example.on_safe.ui.camera

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.on_safe.R
import com.example.onsafe.ui.login.LoginActivity

class CameraModeActivity : AppCompatActivity() {

    private lateinit var previewView: androidx.camera.view.PreviewView
    private lateinit var layoutStandby: LinearLayout
    private lateinit var layoutLiveBadge: LinearLayout
    private lateinit var layoutStatusBadge: LinearLayout
    private lateinit var viewStatusDot: View
    private lateinit var tvStatusText: TextView
    private lateinit var pbConnecting: ProgressBar
    private lateinit var tvGuardianName: TextView
    private lateinit var tvDeviceId: TextView
    private lateinit var btnToggleRecording: Button
    private lateinit var btnLogout: Button
    private lateinit var rootLayout: FrameLayout

    // 화면 보호기 오버레이
    private var screenSaverView: View? = null

    enum class CameraState { STANDBY, CONNECTING, STREAMING, FAILED }
    private var currentState = CameraState.STANDBY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_mode)

        bindViews()
        setState(CameraState.STANDBY)

        btnToggleRecording.setOnClickListener {
            when (currentState) {
                CameraState.STANDBY, CameraState.FAILED -> startRecording()
                CameraState.STREAMING -> stopRecording()
                CameraState.CONNECTING -> { /* 연결 중엔 무시 */ }
            }
        }

        btnLogout.setOnClickListener {
            if (currentState == CameraState.STREAMING || currentState == CameraState.CONNECTING) {
                Toast.makeText(this, "촬영 종료 후 로그아웃해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    private fun bindViews() {
        rootLayout = findViewById(android.R.id.content)
        previewView = findViewById(R.id.previewView)
        layoutStandby = findViewById(R.id.layoutStandby)
        layoutLiveBadge = findViewById(R.id.layoutLiveBadge)
        layoutStatusBadge = findViewById(R.id.layoutStatusBadge)
        viewStatusDot = findViewById(R.id.viewStatusDot)
        tvStatusText = findViewById(R.id.tvStatusText)
        pbConnecting = findViewById(R.id.pbConnecting)
        tvGuardianName = findViewById(R.id.tvGuardianName)
        tvDeviceId = findViewById(R.id.tvDeviceId)
        btnToggleRecording = findViewById(R.id.btnToggleRecording)
        btnLogout = findViewById(R.id.btnLogout)

        // TODO: API에서 보호자 이름, 기기 ID 받아서 채우기
    }

    private fun startRecording() {
        setState(CameraState.CONNECTING)

        // TODO: 실제 CameraX + 서버 연결 로직으로 교체
        // 임시: 2초 후 STREAMING으로 전환 (연결 성공 시뮬레이션)
        Handler(Looper.getMainLooper()).postDelayed({
            setState(CameraState.STREAMING)
        }, 2000)
    }

    private fun stopRecording() {
        // TODO: CameraX 중단, 서버 전송 중단
        setState(CameraState.STANDBY)
    }

    fun setState(state: CameraState) {
        currentState = state
        when (state) {
            CameraState.STANDBY -> {
                layoutStandby.visibility = View.VISIBLE
                layoutLiveBadge.visibility = View.GONE
                pbConnecting.visibility = View.GONE
                setStatusBadge("대기 중", "#9FA6AC")
                btnToggleRecording.text = "촬영 시작하기"
                btnToggleRecording.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#4D80FF"))
                btnToggleRecording.isEnabled = true
                btnToggleRecording.alpha = 1.0f
            }
            CameraState.CONNECTING -> {
                layoutStandby.visibility = View.VISIBLE
                layoutLiveBadge.visibility = View.GONE
                pbConnecting.visibility = View.VISIBLE
                setStatusBadge("연결 중...", "#F59E0B")
                btnToggleRecording.text = "연결 중..."
                btnToggleRecording.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#4D80FF"))
                btnToggleRecording.isEnabled = false
                btnToggleRecording.alpha = 0.4f
            }
            CameraState.STREAMING -> {
                layoutStandby.visibility = View.GONE
                layoutLiveBadge.visibility = View.VISIBLE
                pbConnecting.visibility = View.GONE
                setStatusBadge("전송 중", "#22C55E")
                btnToggleRecording.text = "촬영 종료하기"
                btnToggleRecording.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#EF4444"))
                btnToggleRecording.isEnabled = true
                btnToggleRecording.alpha = 1.0f
            }
            CameraState.FAILED -> {
                layoutStandby.visibility = View.VISIBLE
                layoutLiveBadge.visibility = View.GONE
                pbConnecting.visibility = View.GONE
                setStatusBadge("기기 연결 실패", "#EF4444")
                btnToggleRecording.text = "다시 시도하기"
                btnToggleRecording.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#4D80FF"))
                btnToggleRecording.isEnabled = true
                btnToggleRecording.alpha = 1.0f
            }
        }
    }

    private fun setStatusBadge(text: String, colorHex: String) {
        val color = Color.parseColor(colorHex)
        tvStatusText.text = text
        tvStatusText.setTextColor(color)
        viewStatusDot.background.mutate().setTint(color)
        val bgColor = (color and 0x00FFFFFF) or (0x26 shl 24) // 15% 알파
        layoutStatusBadge.setBackgroundColor(bgColor)
    }

    // ── 화면 보호기 (오버레이 방식) ──────────────────────

    fun showScreenSaver() {
        if (screenSaverView != null) return
        val overlay = LayoutInflater.from(this)
            .inflate(R.layout.activity_screen_saver, rootLayout, false)
        overlay.findViewById<View>(R.id.btnWakeUp).setOnClickListener {
            hideScreenSaver()
        }
        rootLayout.addView(overlay)
        screenSaverView = overlay
    }

    fun hideScreenSaver() {
        screenSaverView?.let {
            rootLayout.removeView(it)
            screenSaverView = null
        }
    }
}