package com.example.on_safe.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.on_safe.R

class RegisterStep1Activity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var btnNext: Button
    private lateinit var layoutAgreeAll: LinearLayout
    private lateinit var layoutAgree1: LinearLayout
    private lateinit var layoutAgree2: LinearLayout
    private lateinit var layoutAgree3: LinearLayout
    private lateinit var layoutAgree4: LinearLayout

    private lateinit var checkAll: View
    private lateinit var check1: View
    private lateinit var check2: View
    private lateinit var check3: View
    private lateinit var check4: View

    private var isCheck1 = false
    private var isCheck2 = false
    private var isCheck3 = false
    private var isCheck4 = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_step1)

        btnBack = findViewById(R.id.btnBack)
        btnNext = findViewById(R.id.btnNext)
        layoutAgreeAll = findViewById(R.id.layoutAgreeAll)
        layoutAgree1 = findViewById(R.id.layoutAgree1)
        layoutAgree2 = findViewById(R.id.layoutAgree2)
        layoutAgree3 = findViewById(R.id.layoutAgree3)
        layoutAgree4 = findViewById(R.id.layoutAgree4)
        checkAll = findViewById(R.id.checkAll)
        check1 = findViewById(R.id.check1)
        check2 = findViewById(R.id.check2)
        check3 = findViewById(R.id.check3)
        check4 = findViewById(R.id.check4)

        btnBack.setOnClickListener { finish() }

        // 전체 동의
        layoutAgreeAll.setOnClickListener {
            val newState = !(isCheck1 && isCheck2 && isCheck3 && isCheck4)
            setCheck(1, newState)
            setCheck(2, newState)
            setCheck(3, newState)
            setCheck(4, newState)
            updateAllCheck()
            updateNextButton()
        }

        layoutAgree1.setOnClickListener { setCheck(1, !isCheck1); updateAllCheck(); updateNextButton() }
        layoutAgree2.setOnClickListener { setCheck(2, !isCheck2); updateAllCheck(); updateNextButton() }
        layoutAgree3.setOnClickListener { setCheck(3, !isCheck3); updateAllCheck(); updateNextButton() }
        layoutAgree4.setOnClickListener { setCheck(4, !isCheck4); updateAllCheck(); updateNextButton() }

        // 다음 버튼 - 필수 3개 동의 시 활성화
        btnNext.setOnClickListener {
            startActivity(Intent(this, RegisterStep2Activity::class.java))
        }
    }

    private fun setCheck(num: Int, checked: Boolean) {
        when (num) {
            1 -> { isCheck1 = checked; check1.setBackgroundResource(if (checked) R.drawable.bg_check_square_checked else R.drawable.bg_check_square_unchecked) }
            2 -> { isCheck2 = checked; check2.setBackgroundResource(if (checked) R.drawable.bg_check_square_checked else R.drawable.bg_check_square_unchecked) }
            3 -> { isCheck3 = checked; check3.setBackgroundResource(if (checked) R.drawable.bg_check_square_checked else R.drawable.bg_check_square_unchecked) }
            4 -> { isCheck4 = checked; check4.setBackgroundResource(if (checked) R.drawable.bg_check_square_checked else R.drawable.bg_check_square_unchecked) }
        }
    }

    private fun updateAllCheck() {
        val allChecked = isCheck1 && isCheck2 && isCheck3 && isCheck4
        checkAll.setBackgroundResource(
            if (allChecked) R.drawable.bg_check_circle_checked
            else R.drawable.bg_check_circle_unchecked
        )
    }

    private fun updateNextButton() {
        // 필수 3개(1,2,3)가 모두 체크되면 다음 버튼 활성화
        val requiredChecked = isCheck1 && isCheck2 && isCheck3
        btnNext.isEnabled = requiredChecked
        btnNext.alpha = if (requiredChecked) 1.0f else 0.4f
    }
}