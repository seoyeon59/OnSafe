package com.example.on_safe.ui.login

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.on_safe.R

// 회원가입 Step1 — 서비스 이용 동의 (필수 3개 + 선택 1개)
class RegisterStep1Activity : AppCompatActivity() {

    // TODO: 서비스 약관 페이지 URL이 확정되면 아래 빈 문자열을 실제 URL로 채워주세요.
    //       URL이 비어 있거나 잘못된 경우 openTermsUrl()이 공통 오류 메시지를 표시합니다.
    private val TERMS_URL_AGREE1 = ""  // 이용약관
    private val TERMS_URL_AGREE2 = ""  // 개인정보 수집 및 이용
    private val TERMS_URL_AGREE3 = ""  // 민감정보(건강·위치 데이터) 처리
    private val TERMS_URL_AGREE5 = ""  // 마케팅 정보 수신

    private lateinit var btnBack: ImageButton
    private lateinit var btnNext: Button

    private lateinit var layoutAgreeAll: LinearLayout
    private lateinit var layoutAgree1: LinearLayout
    private lateinit var layoutAgree2: LinearLayout
    private lateinit var layoutAgree3: LinearLayout
    private lateinit var layoutAgree5: LinearLayout

    private lateinit var checkAll: View
    private lateinit var check1: View
    private lateinit var check2: View
    private lateinit var check3: View
    private lateinit var check5: View

    private lateinit var btnViewAgree1: ImageView
    private lateinit var btnViewAgree2: ImageView
    private lateinit var btnViewAgree3: ImageView
    private lateinit var btnViewAgree5: ImageView

    private var isCheck1 = false
    private var isCheck2 = false
    private var isCheck3 = false
    private var isCheck5 = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_step1)

        bindViews()
        setupListeners()
        refreshUI() // 초기 버튼 상태 보장 (XML 설정에만 의존하지 않도록)
    }

    private fun bindViews() {
        btnBack       = findViewById(R.id.btnBack)
        btnNext       = findViewById(R.id.btnNext)
        layoutAgreeAll = findViewById(R.id.layoutAgreeAll)
        layoutAgree1  = findViewById(R.id.layoutAgree1)
        layoutAgree2  = findViewById(R.id.layoutAgree2)
        layoutAgree3  = findViewById(R.id.layoutAgree3)
        layoutAgree5  = findViewById(R.id.layoutAgree5)
        checkAll      = findViewById(R.id.checkAll)
        check1        = findViewById(R.id.check1)
        check2        = findViewById(R.id.check2)
        check3        = findViewById(R.id.check3)
        check5        = findViewById(R.id.check5)

        btnViewAgree1 = findViewById(R.id.btnViewAgree1)
        btnViewAgree2 = findViewById(R.id.btnViewAgree2)
        btnViewAgree3 = findViewById(R.id.btnViewAgree3)
        btnViewAgree5 = findViewById(R.id.btnViewAgree5)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        // 전체 동의: 현재 모든 항목이 체크됐으면 전부 해제, 아니면 전부 체크
        layoutAgreeAll.setOnClickListener {
            val allChecked = isCheck1 && isCheck2 && isCheck3 && isCheck5
            val newState = !allChecked
            setCheck(1, newState)
            setCheck(2, newState)
            setCheck(3, newState)
            setCheck(5, newState)
            refreshUI()
        }

        layoutAgree1.setOnClickListener { setCheck(1, !isCheck1); refreshUI() }
        layoutAgree2.setOnClickListener { setCheck(2, !isCheck2); refreshUI() }
        layoutAgree3.setOnClickListener { setCheck(3, !isCheck3); refreshUI() }
        layoutAgree5.setOnClickListener { setCheck(5, !isCheck5); refreshUI() }

        // 각 약관의 화살표 버튼 클릭 → URL 열기 (체크박스 토글과 독립 동작)
        btnViewAgree1.setOnClickListener { openTermsUrl(TERMS_URL_AGREE1) }
        btnViewAgree2.setOnClickListener { openTermsUrl(TERMS_URL_AGREE2) }
        btnViewAgree3.setOnClickListener { openTermsUrl(TERMS_URL_AGREE3) }
        btnViewAgree5.setOnClickListener { openTermsUrl(TERMS_URL_AGREE5) }

        btnNext.setOnClickListener {
            startActivity(Intent(this, RegisterStep2Activity::class.java))
            overridePendingTransition(R.anim.detail_enter, R.anim.detail_exit)
        }
    }

    // ──────────────────────────────────────────────
    // UI 업데이트
    // ──────────────────────────────────────────────

    private fun setCheck(num: Int, checked: Boolean) {
        val res = if (checked) R.drawable.bg_check_square_checked else R.drawable.bg_check_square_unchecked
        when (num) {
            1 -> { isCheck1 = checked; check1.setBackgroundResource(res) }
            2 -> { isCheck2 = checked; check2.setBackgroundResource(res) }
            3 -> { isCheck3 = checked; check3.setBackgroundResource(res) }
            5 -> { isCheck5 = checked; check5.setBackgroundResource(res) }
        }
    }

    // 약관 URL 열기 — URL이 없거나 브라우저를 열 수 없을 때 공통 오류 메시지 표시
    private fun openTermsUrl(url: String) {
        if (url.isBlank()) {
            Toast.makeText(this, "약관 페이지가 아직 준비 중입니다.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "약관 페이지를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // 전체 동의 체크 + 다음 버튼 상태를 한 번에 갱신
    private fun refreshUI() {
        val allChecked = isCheck1 && isCheck2 && isCheck3 && isCheck5
        checkAll.setBackgroundResource(
            if (allChecked) R.drawable.bg_check_square_checked else R.drawable.bg_check_square_unchecked
        )

        // 다음 버튼: 필수 3개(1~3) 모두 체크 시 활성
        val requiredChecked = isCheck1 && isCheck2 && isCheck3
        btnNext.isEnabled = requiredChecked
        btnNext.alpha = if (requiredChecked) 1.0f else 0.4f
    }

    // 좌상단 뒤로가기 버튼이 있는 화면 공통 — 알림 화면과 동일한 "파고들어왔다 빠져나가는" 전환
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.detail_pop_enter, R.anim.detail_pop_exit)
    }
}
