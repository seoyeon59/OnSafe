package com.example.on_safe.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.example.on_safe.R
import com.example.on_safe.util.TermsLinks
import com.example.on_safe.util.openTermsUrl
import com.example.on_safe.util.setEnabledWithAlpha

// 회원가입 Step1 — 서비스 이용 동의 (필수 3개 + 선택 1개)
class RegisterStep1Activity : AppCompatActivity() {

    // 동의 항목 하나 — 행 클릭은 체크 토글, 화살표 클릭은 약관 열기
    private class AgreeItem(
        val row: View,
        val check: View,
        val arrow: View,
        val url: String,
        val required: Boolean
    ) {
        var checked = false
    }

    private lateinit var btnNext: Button
    private lateinit var checkAll: View
    private lateinit var items: List<AgreeItem>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_step1)

        btnNext = findViewById(R.id.btnNext)
        checkAll = findViewById(R.id.checkAll)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val marketing = agreeItem(R.id.layoutAgree5, R.id.check5, R.id.btnViewAgree5, TermsLinks.MARKETING, required = false)
        items = listOf(
            agreeItem(R.id.layoutAgree1, R.id.check1, R.id.btnViewAgree1, TermsLinks.SERVICE, required = true),
            agreeItem(R.id.layoutAgree2, R.id.check2, R.id.btnViewAgree2, TermsLinks.PRIVACY, required = true),
            agreeItem(R.id.layoutAgree3, R.id.check3, R.id.btnViewAgree3, TermsLinks.SENSITIVE, required = true),
            marketing
        )

        items.forEach { item ->
            item.row.setOnClickListener { setChecked(item, !item.checked) }
            // 화살표는 체크 토글과 독립 — 약관 페이지만 열기
            item.arrow.setOnClickListener { openTermsUrl(item.url) }
        }

        // 전체 동의 — 전부 체크된 상태면 해제, 아니면 전부 체크
        findViewById<View>(R.id.layoutAgreeAll).setOnClickListener {
            val next = !items.all { it.checked }
            items.forEach { it.checked = next; it.check.setBackgroundResource(checkDrawable(next)) }
            refreshUI()
        }

        btnNext.setOnClickListener {
            startActivity(
                Intent(this, RegisterStep2Activity::class.java).apply {
                    // 마케팅 수신 동의(선택)는 필수 항목과 별개로 Step2 → 서버까지 전달
                    putExtra(RegisterStep2Activity.EXTRA_MARKETING_CONSENT, marketing.checked)
                }
            )
            overridePendingTransition(R.anim.detail_enter, R.anim.detail_exit)
        }

        refreshUI()   // 초기 버튼 상태 보장 (XML 설정에만 의존하지 않도록)
    }

    private fun agreeItem(rowId: Int, checkId: Int, arrowId: Int, url: String, required: Boolean) =
        AgreeItem(findViewById(rowId), findViewById(checkId), findViewById(arrowId), url, required)

    private fun setChecked(item: AgreeItem, checked: Boolean) {
        item.checked = checked
        item.check.setBackgroundResource(checkDrawable(checked))
        refreshUI()
    }

    // 전체 동의 체크 + 다음 버튼 상태 일괄 갱신
    private fun refreshUI() {
        checkAll.setBackgroundResource(checkDrawable(items.all { it.checked }))
        // 다음 버튼: 필수 항목 전부 체크 시 활성
        btnNext.setEnabledWithAlpha(items.all { !it.required || it.checked })
    }

    private fun checkDrawable(checked: Boolean) =
        if (checked) R.drawable.bg_check_square_checked else R.drawable.bg_check_square_unchecked

    // 좌상단 뒤로가기 화면 공통 전환 — 알림 화면과 동일
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.detail_pop_enter, R.anim.detail_pop_exit)
    }
}
