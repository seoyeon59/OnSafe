package com.example.on_safe.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.on_safe.R

/**
 * 회원가입 Step1 — 서비스 이용 동의
 *
 * 약관 목록 (총 5개):
 *  1. 이용약관 동의 (필수)
 *  2. 개인정보 수집 및 이용 동의 (필수)
 *  3. 민감정보(건강·위치 데이터) 처리 동의 (필수)
 *  4. 서비스 이용 책임 안내 동의 (필수)  ← 신규
 *  5. 마케팅 정보 수신 동의 (선택)
 *
 * 다음 버튼: 필수 4개(1~4) 모두 동의 시 활성화
 */
class RegisterStep1Activity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var btnNext: Button

    private lateinit var layoutAgreeAll: LinearLayout
    private lateinit var layoutAgree1: LinearLayout
    private lateinit var layoutAgree2: LinearLayout
    private lateinit var layoutAgree3: LinearLayout
    private lateinit var layoutAgree4: LinearLayout
    private lateinit var layoutAgree5: LinearLayout

    private lateinit var checkAll: View
    private lateinit var check1: View
    private lateinit var check2: View
    private lateinit var check3: View
    private lateinit var check4: View
    private lateinit var check5: View

    // 체크 상태 (비트 패킹 없이 명시적 변수 — 디버그 가독성 우선)
    private var isCheck1 = false
    private var isCheck2 = false
    private var isCheck3 = false
    private var isCheck4 = false
    private var isCheck5 = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_step1)

        bindViews()
        setupListeners()
    }

    private fun bindViews() {
        btnBack       = findViewById(R.id.btnBack)
        btnNext       = findViewById(R.id.btnNext)
        layoutAgreeAll = findViewById(R.id.layoutAgreeAll)
        layoutAgree1  = findViewById(R.id.layoutAgree1)
        layoutAgree2  = findViewById(R.id.layoutAgree2)
        layoutAgree3  = findViewById(R.id.layoutAgree3)
        layoutAgree4  = findViewById(R.id.layoutAgree4)
        layoutAgree5  = findViewById(R.id.layoutAgree5)
        checkAll      = findViewById(R.id.checkAll)
        check1        = findViewById(R.id.check1)
        check2        = findViewById(R.id.check2)
        check3        = findViewById(R.id.check3)
        check4        = findViewById(R.id.check4)
        check5        = findViewById(R.id.check5)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        // 전체 동의: 현재 모든 항목이 체크됐으면 전부 해제, 아니면 전부 체크
        layoutAgreeAll.setOnClickListener {
            val allChecked = isCheck1 && isCheck2 && isCheck3 && isCheck4 && isCheck5
            val newState = !allChecked
            setCheck(1, newState)
            setCheck(2, newState)
            setCheck(3, newState)
            setCheck(4, newState)
            setCheck(5, newState)
            refreshUI()
        }

        layoutAgree1.setOnClickListener { setCheck(1, !isCheck1); refreshUI() }
        layoutAgree2.setOnClickListener { setCheck(2, !isCheck2); refreshUI() }
        layoutAgree3.setOnClickListener { setCheck(3, !isCheck3); refreshUI() }
        layoutAgree4.setOnClickListener { setCheck(4, !isCheck4); refreshUI() }
        layoutAgree5.setOnClickListener { setCheck(5, !isCheck5); refreshUI() }

        btnNext.setOnClickListener {
            startActivity(Intent(this, RegisterStep2Activity::class.java))
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
            4 -> { isCheck4 = checked; check4.setBackgroundResource(res) }
            5 -> { isCheck5 = checked; check5.setBackgroundResource(res) }
        }
    }

    /** 전체 동의 체크 + 다음 버튼 상태를 한 번에 갱신 */
    private fun refreshUI() {
        // 전체 동의 아이콘
        val allChecked = isCheck1 && isCheck2 && isCheck3 && isCheck4 && isCheck5
        checkAll.setBackgroundResource(
            if (allChecked) R.drawable.bg_circle_primary else R.drawable.bg_circle_outline
        )

        // 다음 버튼: 필수 4개(1~4) 모두 체크 시 활성
        val requiredChecked = isCheck1 && isCheck2 && isCheck3 && isCheck4
        btnNext.isEnabled = requiredChecked
        btnNext.alpha = if (requiredChecked) 1.0f else 0.4f
    }
}
