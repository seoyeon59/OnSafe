package com.example.on_safe.ui.tutorial

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.isVisible
import com.example.on_safe.R
import com.example.on_safe.ui.login.PermissionActivity

// 튜토리얼 화면
// 진입 경로 1: 최초 로그인 성공 후 (fromLogin=true) → 완료 시 권한 화면 이동 + 플래그 저장
// 진입 경로 2: 설정 / 카메라 모드 우측 상단 정보 버튼 (fromLogin=false) → 완료 시 finish()
class TutorialActivity : AppCompatActivity() {

    // 페이지 데이터 모델 — imageResId = null 이면 이미지 자리를 비움
    private data class TutorialPage(val imageResId: Int? = null)

    // 항목을 추가/삭제하면 진행 바·카운터·버튼 문구가 모두 자동 반영
    private val pages: List<TutorialPage> = listOf(
        TutorialPage(),   // 1페이지
        TutorialPage(),   // 2페이지
        TutorialPage(),   // 3페이지
        TutorialPage(),   // 4페이지
        TutorialPage(),   // 5페이지
        TutorialPage(),   // 6페이지
    )

    private val totalPages get() = pages.size

    private var currentPage = 0

    // true: 로그인 후 최초 진입 / false: 설정·카메라 모드에서 재진입
    private var fromLogin = false

    private lateinit var progressBar: ProgressBar
    private lateinit var tvCounter: TextView
    private lateinit var ivPage: ImageView
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutorial)

        fromLogin = intent.getBooleanExtra(EXTRA_FROM_LOGIN, false)

        progressBar = findViewById(R.id.progressBar)
        tvCounter = findViewById(R.id.tvPageCounter)
        ivPage = findViewById(R.id.ivPageImage)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)

        btnPrev.setOnClickListener { navigateTo(currentPage - 1) }
        btnNext.setOnClickListener {
            if (currentPage < totalPages - 1) navigateTo(currentPage + 1) else exitTutorial(completed = true)
        }
        // X 버튼: 끝까지 보지 않고 이탈
        findViewById<ImageButton>(R.id.btnSkipTutorial).setOnClickListener { exitTutorial(completed = false) }

        // 저장된 상태 복원 (화면 회전 등)
        currentPage = savedInstanceState?.getInt(STATE_CURRENT_PAGE) ?: 0
        renderPage(currentPage)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_CURRENT_PAGE, currentPage)
    }

    private fun navigateTo(page: Int) {
        if (page !in 0 until totalPages) return
        currentPage = page
        renderPage(currentPage)
    }

    private fun renderPage(page: Int) {
        // setProgress(value, animate=true) — minSdk 24+라 별도 분기 불필요
        progressBar.setProgress((page + 1) * 100 / totalPages, true)
        tvCounter.text = "${page + 1}/$totalPages"

        val imageResId = pages[page].imageResId
        ivPage.isVisible = imageResId != null
        if (imageResId != null) ivPage.setImageResource(imageResId)

        // 첫 페이지는 이전 버튼 gone — 공간을 없애 btnNext가 전체 너비 차지
        btnPrev.isVisible = page != 0
        btnNext.text = getString(
            if (page == totalPages - 1) R.string.tutorial_btn_complete else R.string.tutorial_btn_next
        )
    }

    /**
     * 튜토리얼 종료 — 로그인 플로우 중이면 완료·이탈 무관하게 권한 화면으로 연결.
     * 자동 표시 플래그는 완주 시에만 기록 — 중간 이탈은 다음 로그인에 재표시.
     */
    private fun exitTutorial(completed: Boolean) {
        if (completed && fromLogin) markTutorialShown()
        if (fromLogin) {
            startActivity(
                Intent(this, PermissionActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
        }
        finish()
    }

    private fun markTutorialShown() {
        prefs(this).edit { putBoolean(KEY_TUTORIAL_SHOWN, true) }
    }

    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_TUTORIAL_SHOWN = "tutorial_shown"

        private const val EXTRA_FROM_LOGIN = "extra_from_login"
        private const val STATE_CURRENT_PAGE = "state_current_page"

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 최초 로그인 후 표시용 (모드 선택 전이므로 selectedMode 불필요)
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
            prefs(context).getBoolean(KEY_TUTORIAL_SHOWN, false)

        // 디버그 로그인이 온보딩을 처음부터 다시 태우기 위해 호출
        fun resetShownFlag(context: Context) {
            prefs(context).edit { remove(KEY_TUTORIAL_SHOWN) }
        }
    }
}
