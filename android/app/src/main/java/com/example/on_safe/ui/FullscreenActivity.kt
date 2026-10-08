package com.example.on_safe.ui

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.on_safe.R
import com.example.on_safe.data.repository.WardSource
import com.example.on_safe.ui.live.LiveViewController
import com.example.on_safe.ui.live.LiveViewState
import com.example.on_safe.util.TokenManager
import com.example.on_safe.util.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import livekit.org.webrtc.RendererCommon

class FullscreenActivity : AppCompatActivity() {

    private var lastHintTime = 0L

    private lateinit var liveController: LiveViewController

    // 홈에서 넘겨받은 피보호자 — 없으면(재생성 등) 직접 조회
    private var wardUserId: String? = null

    // 뒤로가기로 홈에 돌아갈 때 시청을 넘긴다 — 서버 세션을 닫지 않고 홈의 POST가 연장으로 합류
    private var handingOffToHome = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 가로 방향은 매니페스트의 sensorLandscape로 처리 (여기서 고정하면 양방향 회전이 막힘)
        setContentView(R.layout.activity_fullscreen)

        wardUserId = intent.getStringExtra(EXTRA_WARD_USER_ID)
        liveController = LiveViewController(
            context = this,
            scope = lifecycleScope,
            videoFrame = findViewById<FrameLayout>(R.id.liveVideoFrame),
            // 전체화면은 잘림 없이 전체 장면을 보여준다
            scalingType = RendererCommon.ScalingType.SCALE_ASPECT_FIT,
            onState = ::renderLiveState
        )

        showExitHint()
        findViewById<ImageButton>(R.id.btnBackFullscreen).setOnClickListener { finish() }
        findViewById<View>(R.id.btnLiveRetry).setOnClickListener { startLive() }
    }

    // 전체화면 진입 자체가 시청 요청 — 포그라운드가 될 때마다 시작
    override fun onStart() {
        super.onStart()
        handingOffToHome = false
        startLive()
    }

    // 백그라운드 전환·화면 이탈 시 서버 세션 종료(DELETE). 홈으로 넘기는 경우만 유지
    override fun onStop() {
        super.onStop()
        liveController.stop(endSession = !handingOffToHome)
    }

    private fun startLive() {
        val ward = wardUserId
        if (ward != null) {
            liveController.start(ward)
            return
        }
        renderLiveState(LiveViewState.Connecting)
        lifecycleScope.launch {
            val resolved = try {
                WardSource.fetchPairedWardUserId(TokenManager.getUserId(this@FullscreenActivity))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                renderLiveState(LiveViewState.Stopped("연결된 피보호자를 확인하지 못했습니다."))
                return@launch
            }
            if (resolved == null) {
                renderLiveState(LiveViewState.Stopped("피보호자를 연결하면 실시간 영상을 볼 수 있습니다."))
                return@launch
            }
            wardUserId = resolved
            liveController.start(resolved)
        }
    }

    private fun renderLiveState(state: LiveViewState) {
        val overlay = findViewById<View>(R.id.layoutLiveOverlay)
        val progress = findViewById<View>(R.id.pbLive)
        val message = findViewById<TextView>(R.id.tvLiveMessage)
        val retry = findViewById<View>(R.id.btnLiveRetry)
        findViewById<View>(R.id.layoutFullscreenLiveBadge).isVisible = state == LiveViewState.Live

        when (state) {
            LiveViewState.Idle -> {
                overlay.isVisible = true
                progress.isVisible = false
                message.text = ""
                retry.isVisible = false
            }
            LiveViewState.Connecting -> {
                overlay.isVisible = true
                progress.isVisible = true
                message.text = "피보호자 카메라 영상을 요청하는 중…"
                retry.isVisible = false
            }
            LiveViewState.Live -> overlay.isVisible = false
            is LiveViewState.Stopped -> {
                // 더 볼 수 없는 종료(동의 철회·서버 종료)는 화면을 닫는다
                if (state.closeScreen) {
                    state.message?.let { toast(it) }
                    finish()
                    return
                }
                overlay.isVisible = true
                progress.isVisible = false
                message.text = state.message.orEmpty()
                retry.isVisible = true
            }
        }
    }

    // 뒤로가기·닫기 모두 이 finish()를 거친다 — 퇴장은 회전 대신 detail_pop 전환 사용
    override fun finish() {
        // 보는 중이면 홈이 이어서 보도록 넘긴다(홈은 결과를 받아 같은 세션에 합류)
        handingOffToHome = liveController.isRunning
        setResult(RESULT_OK, Intent().putExtra(EXTRA_CONTINUE_LIVE, handingOffToHome))
        super.finish()
        overridePendingTransition(R.anim.detail_pop_enter, R.anim.detail_pop_exit)
    }

    // 전체화면에는 안내 문구를 띄울 자리가 없어 터치할 때마다 재출력
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) showExitHint()
        return super.onTouchEvent(event)
    }

    // 쿨타임 — 연속 터치로 토스트가 줄줄이 쌓이는 것 방지 (진입 직후 터치도 동일 적용)
    private fun showExitHint() {
        val now = System.currentTimeMillis()
        if (now - lastHintTime < HINT_COOLDOWN_MS) return
        lastHintTime = now
        toast("나가려면 뒤로가기를 누르세요")
    }

    companion object {
        const val EXTRA_WARD_USER_ID = "ward_user_id"

        // 결과: 홈이 시청을 이어받아야 하는지
        const val EXTRA_CONTINUE_LIVE = "continue_live"

        private const val HINT_COOLDOWN_MS = 1500L
    }
}
