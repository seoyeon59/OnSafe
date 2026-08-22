package com.example.on_safe.ui.tutorial

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.on_safe.R

// 튜토리얼 화면
// 진입 경로 1: 최초 로그인 성공 후 (fromLogin=true) → 완료 시 ModeSelectActivity 이동 + 플래그 저장
// 진입 경로 2: 설정 / 카메라 모드 우측 상단 정보 버튼 (fromLogin=false) → 완료 시 finish()
class TutorialActivity : AppCompatActivity() {

    // ──────────────────────────────────────────────
    // 페이지 데이터 모델 — imageResId = null 이면 회색 placeholder 표시
    private data class TutorialPage(
        val imageResId: Int? = null          // null → 회색 placeholder
        // 추후 확장: val titleResId: Int? = null,
        //            val descResId:  Int? = null
    )

    // ── 페이지 목록: 항목을 추가/삭제하면 모든 UI가 자동 반영 ──
    private val pages: List<TutorialPage> = listOf(
        TutorialPage(),   // 1페이지
        TutorialPage(),   // 2페이지
        TutorialPage(),   // 3페이지
        TutorialPage(),   // 4페이지
        TutorialPage(),   // 5페이지
        TutorialPage(),   // 6페이지
    )

    private val totalPages get() = pages.size

    // ──────────────────────────────────────────────
    // 상태
    // ──────────────────────────────────────────────
    private var currentPage = 0

    // true: 로그인 후 최초 진입 / false: 설정·카메라 모드에서 재진입
    private var fromLogin = false

    // selectedMode는 더 이상 사용하지 않음 (튜토리얼은 모드 선택 전에 표시)

    // ──────────────────────────────────────────────
    // Views
    // ──────────────────────────────────────────────
    private lateinit var progressBar:      ProgressBar
    private lateinit var tvCounter:        TextView
    private lateinit var ivPage:           ImageView
    private lateinit var btnPrev:          Button
    private lateinit var btnNext:          Button
    private lateinit var btnSkipTutorial:  ImageButton

    // ──────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutorial)

        fromLogin = intent.getBooleanExtra(EXTRA_FROM_LOGIN, false)

        progressBar      = findViewById(R.id.progressBar)
        tvCounter        = findViewById(R.id.tvPageCounter)
        ivPage           = findViewById(R.id.ivPageImage)
        btnPrev          = findViewById(R.id.btnPrev)
        btnNext          = findViewById(R.id.btnNext)
        btnSkipTutorial  = findViewById(R.id.btnSkipTutorial)

        btnPrev.setOnClickListener { navigateTo(currentPage - 1) }
        btnNext.setOnClickListener {
            if (currentPage < totalPages - 1) {
                navigateTo(currentPage + 1)
            } else {
                onTutorialComplete()
            }
        }

        // X 버튼: 튜토리얼을 끝까지 보지 않고 이탈
        btnSkipTutorial.setOnClickListener { onSkipTutorial() }

        // 저장된 상태 복원 (화면 회전 등)
        currentPage = savedInstanceState?.getInt(STATE_CURRENT_PAGE) ?: 0
        renderPage(currentPage)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_CURRENT_PAGE, currentPage)
    }

    // ──────────────────────────────────────────────
    // 페이지 렌더링
    // ──────────────────────────────────────────────

    private fun navigateTo(page: Int) {
        if (page !in 0 until totalPages) return
        currentPage = page
        renderPage(currentPage)
    }

    private fun renderPage(page: Int) {
        // 진행 바 — setProgress(value, animate=true)로 부드럽게 채워지도록 (minSdk 24+라 별도 분기 불필요)
        progressBar.setProgress((page + 1) * 100 / totalPages, true)

        // 카운터
        tvCounter.text = "${page + 1}/$totalPages"

        // 이미지
        val p = pages[page]
        if (p.imageResId != null) {
            ivPage.setImageResource(p.imageResId)
            ivPage.visibility = View.VISIBLE
        } else {
            ivPage.visibility = View.GONE
        }

        // 이전 버튼: 첫 페이지 gone (공간 없앰 → btnNext 전체 너비 차지), 나머지 visible
        btnPrev.visibility = if (page == 0) View.GONE else View.VISIBLE

        // 다음/완료 버튼
        btnNext.text = if (page == totalPages - 1)
            getString(R.string.tutorial_btn_complete)
        else
            getString(R.string.tutorial_btn_next)
    }

    // ──────────────────────────────────────────────
    // 완료 처리
    // ──────────────────────────────────────────────

    private fun onTutorialComplete() {
        if (fromLogin) {
            // 최초 실행 플래그 저장 (이후에는 튜토리얼 자동 표시 안 함)
            markTutorialShown()
            // 권한 요청 화면으로 이동 (플로우: 튜토리얼 → 권한 → 모드 선택)
            startActivity(
                Intent(this, com.example.on_safe.ui.login.PermissionActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
        }
        finish()
    }

    // X 버튼으로 중간 이탈 — markTutorialShown() 호출 안 함 (다음 로그인 때 다시 보임)
    private fun onSkipTutorial() {
        if (fromLogin) {
            // 로그인 플로우 중이므로 PermissionActivity로 넘어가야 앱이 계속 진행됨
            startActivity(
                Intent(this, com.example.on_safe.ui.login.PermissionActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
        }
        finish()
    }

    // ──────────────────────────────────────────────
    // SharedPreferences 헬퍼
    // ──────────────────────────────────────────────

    private fun markTutorialShown() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TUTORIAL_SHOWN, true)
            .apply()
    }

    // ──────────────────────────────────────────────
    // Companion (진입 팩토리 + 상수)
    // ──────────────────────────────────────────────

    companion object {
        private const val PREFS_NAME          = "app_prefs"
        const  val KEY_TUTORIAL_SHOWN         = "tutorial_shown"

        private const val EXTRA_FROM_LOGIN   = "extra_from_login"
        private const val STATE_CURRENT_PAGE = "state_current_page"

        // 최초 로그인 후 표시용 Intent (모드 선택 전이므로 selectedMode 불필요)
        fun intentForLogin(from: Context): Intent =
            Intent(from, TutorialActivity::class.java).apply {
                putExtra(EXTRA_FROM_LOGIN, true)
            }

        // 설정·카메라 모드에서 언제든지 진입하는 경로
        fun intentFromSettings(from: Context): Intent =
            Intent(from, TutorialActivity::class.java).apply {
                putExtra(EXTRA_FROM_LOGIN, false)
            }

        fun isTutorialShown(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_TUTORIAL_SHOWN, false)
    }
}
