package com.example.on_safe.ui.login

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.on_safe.R
import com.example.on_safe.network.dto.JusoItem
import com.example.on_safe.util.toast

class AddressSearchActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_ZIP = "zipNo"
    }

    private val viewModel: AddressSearchViewModel by viewModels()

    private lateinit var etKeyword: EditText
    private lateinit var rvAddress: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var layoutEmptyState: View
    private lateinit var tipCard: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_address_search)

        etKeyword = findViewById(R.id.etKeyword)
        rvAddress = findViewById(R.id.rvAddress)
        tvEmpty = findViewById(R.id.tvEmpty)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        tipCard = findViewById(R.id.tipCard)

        // 카드 간 마진(item_address.xml)으로만 구분 — 별도 구분선 없음
        rvAddress.layoutManager = LinearLayoutManager(this)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnSearch).setOnClickListener { search() }

        // 키보드 검색 버튼으로도 검색 실행
        etKeyword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search()
                true
            } else {
                false
            }
        }

        // 상태 관찰 후 화면 갱신만 — 검색 로직·서버 통신은 뷰모델 담당
        viewModel.uiState.observe(this) { state ->
            when (state) {
                is AddressSearchUiState.Tip -> showOnly(tip = true)
                is AddressSearchUiState.Results -> showResults(state.list)
                is AddressSearchUiState.Empty -> {
                    tvEmpty.text = state.message
                    showOnly(empty = true)
                }
            }
        }
    }

    private fun search() {
        val keyword = etKeyword.text.toString().trim()
        if (keyword.isEmpty()) {
            toast("검색어를 입력해주세요.")
            return
        }
        viewModel.search(keyword)
    }

    private fun showResults(list: List<JusoItem>) {
        showOnly(results = true)
        rvAddress.adapter = AddressAdapter(list) { item ->
            setResult(
                Activity.RESULT_OK,
                Intent().apply {
                    putExtra(EXTRA_ADDRESS, item.roadAddr)
                    putExtra(EXTRA_ZIP, item.zipNo)
                }
            )
            finish()
        }
    }

    // 팁 안내 / 결과 목록 / 결과 없음 중 하나만 노출
    private fun showOnly(tip: Boolean = false, results: Boolean = false, empty: Boolean = false) {
        tipCard.isVisible = tip
        rvAddress.isVisible = results
        layoutEmptyState.isVisible = empty
    }

    // 좌상단 뒤로가기 화면 공통 전환 — 알림 화면과 동일
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.detail_pop_enter, R.anim.detail_pop_exit)
    }
}
