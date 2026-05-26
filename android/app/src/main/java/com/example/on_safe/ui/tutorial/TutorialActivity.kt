package com.example.on_safe.ui.tutorial

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.on_safe.R

/**
 * 튜토리얼 화면.
 *
 * ┌──────────────────────────────────────────────┐
 * │ 진입 경로 1: 최초 로그인 → 권한 요구(Permission) 전 │
 * │ 진입 경로 2: 설정 우측 상단 정보 버튼 (언제든지)     │
 * └──────────────────────────────────────────────┘
 *
 * 페이지 추가 방법:
 *   pages 리스트에 TutorialPage 항목을 추가하면 진행 바·카운터·버튼이 자동 반영됨.
 *
 * 성능 고려사항:
 *   - 페이지 전환은 단순 뷰 업데이트(View.setImageResource) — ViewPager 오버헤드 없음.
 *   - SharedPreferences 쓰기는 완료(또는 건너뜀) 시 1회만 수행.
 *   - pages 리스트는 val + data class → 불변, 재할당 없음.
 */
class TutorialActivity : AppCompatActivity() {

    // ──────────────────────────────────────────────
    // 페이지 데이터 모델
    // 이미지 리소스 준비 전에는 imageResId = null (회색 placeholder 표시)
    // 타이틀/설명은 추후 추가 가능한 필드로 미리 선언
    // ──────────────────────────────────────────────
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

    /**
     * true  → 최초 로그인 플로우 (완료 시 PermissionActivity 이동 + 플래그 저장)
     * false → 설정에서 진입 (완료 시 그냥 finish)
     */
    private var fromLogin = false

    /** ModeSelectActivity에서 전달받은 모드 (1=보호자, 2=카메라) */
    private var selectedMode = 1

    // ──────────────────────────────────────────────
    // Views
    // ──────────────────────────────────────────────
    private lateinit var progressBar:  ProgressBar
    private lateinit var tvCounter:    TextView
    private lateinit var ivPage:       ImageView
    private lateinit var btnPrev:      Button
    private lateinit var btnNext:      Button

    // ──────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutorial)

        fromLogin    = intent.getBooleanExtra(EXTRA_FROM_LOGIN, false)
        selectedMode = intent.getIntExtra(EXTRA_SELECTED_MODE, 1)

        progressBar = findViewById(R.id.progressBar)
        tvCounter   = findViewById(R.id.tvPageCounter)
        ivPage      = findViewById(R.id.ivPageImage)
        btnPrev     = findViewById(R.id.btnPrev)
        btnNext     = findViewById(R.id.btnNext)

        btnPrev.setOnClickListener { navigateTo(currentPage - 1) }
        btnNext.setOnClickListener {
            if (currentPage < totalPages - 1) {
                navigateTo(currentPage + 1)
            } else {
                onTutorialComplete()
            }
        }

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

    /**
     * 주어진 페이지 인덱스에 맞춰 모든 UI 요소를 한 번에 업데이트한다.
     * - Progress bar: (page+1)/totalPages * 100
     * - Counter:      "page+1/totalPages"
     * - Image:        imageResId가 있으면 표시, 없으면 placeholder
     * - 이전 버튼:    첫 페이지는 invisible (레이아웃 공간 유지), 이후 visible
     * - 다음 버튼:    마지막 페이지는 "완료", 이외는 "다음"
     */
    private fun renderPage(page: Int) {
        // 진행 바
        progressBar.progress = (page + 1) * 100 / totalPages

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
            // 권한 요구 화면으로 이동
            val intent = Intent(
                this,
                com.example.on_safe.ui.login.PermissionActivity::class.java
            ).apply {
                putExtra("selected_mode", selectedMode)
            }
            startActivity(intent)
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

        private const val EXTRA_FROM_LOGIN    = "extra_from_login"
        private const val EXTRA_SELECTED_MODE = "extra_selected_mode"
        private const val STATE_CURRENT_PAGE  = "state_current_page"

        /**
         * 최초 로그인 플로우용 Intent
         * @param selectedMode ModeSelectActivity에서 선택한 모드 (1=보호자, 2=카메라)
         */
        fun intentForLogin(from: Context, selectedMode: Int): Intent =
            Intent(from, TutorialActivity::class.java).apply {
                putExtra(EXTRA_FROM_LOGIN,    true)
                putExtra(EXTRA_SELECTED_MODE, selectedMode)
            }

        /**
         * 설정 화면 정보 버튼용 Intent (언제든지 볼 수 있는 경로)
         */
        fun intentFromSettings(from: Context): Intent =
            Intent(from, TutorialActivity::class.java).apply {
                putExtra(EXTRA_FROM_LOGIN, false)
            }

        /**
         * SharedPreferences에서 튜토리얼 표시 여부를 읽는다.
         * ModeSelectActivity 등 외부에서 호출 가능.
         */
        fun isTutorialShown(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_TUTORIAL_SHOWN, false)
    }
}
