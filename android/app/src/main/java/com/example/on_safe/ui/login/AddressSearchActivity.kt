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
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.on_safe.R
import com.example.on_safe.network.dto.JusoItem

class AddressSearchActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_ZIP     = "zipNo"
    }

    private val viewModel: AddressSearchViewModel by viewModels()

    private lateinit var etKeyword:  EditText
    private lateinit var btnSearch:  Button
    private lateinit var btnBack:    ImageButton
    private lateinit var rvAddress:  RecyclerView
    private lateinit var tvEmpty:    TextView
    private lateinit var tipCard:    LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_address_search)

        etKeyword = findViewById(R.id.etKeyword)
        btnSearch = findViewById(R.id.btnSearch)
        btnBack   = findViewById(R.id.btnBack)
        rvAddress = findViewById(R.id.rvAddress)
        tvEmpty   = findViewById(R.id.tvEmpty)
        tipCard   = findViewById(R.id.tipCard)

        rvAddress.layoutManager = LinearLayoutManager(this)
        // 카드 간 마진(item_address.xml layout_marginBottom)으로만 구분 — 별도 구분선 없음

        btnBack.setOnClickListener { finish() }
        btnSearch.setOnClickListener { search() }

        // 키보드 검색 버튼으로도 검색 실행
        etKeyword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search(); true
            } else false
        }

        // 뷰모델 상태를 관찰해서 화면만 갱신 — 검색 로직/서버 통신은 전부 뷰모델 쪽으로 이동
        viewModel.uiState.observe(this) { state ->
            when (state) {
                is AddressSearchUiState.Tip -> showTip()
                is AddressSearchUiState.Results -> showResults(state.list)
                is AddressSearchUiState.Empty -> showEmpty(state.message)
            }
        }
    }

    private fun search() {
        val keyword = etKeyword.text.toString().trim()
        if (keyword.isEmpty()) {
            Toast.makeText(this, "검색어를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.search(keyword)
    }

    private fun showResults(list: List<JusoItem>) {
        tipCard.visibility   = View.GONE
        tvEmpty.visibility   = View.GONE
        rvAddress.visibility = View.VISIBLE
        rvAddress.adapter = AddressAdapter(list) { item ->
            val result = Intent().apply {
                putExtra(EXTRA_ADDRESS, item.roadAddr)
                putExtra(EXTRA_ZIP, item.zipNo)
            }
            setResult(Activity.RESULT_OK, result)
            finish()
        }
    }

    private fun showEmpty(msg: String) {
        tipCard.visibility   = View.GONE
        rvAddress.visibility = View.GONE
        tvEmpty.visibility   = View.VISIBLE
        tvEmpty.text         = msg
    }

    private fun showTip() {
        tipCard.visibility   = View.VISIBLE
        rvAddress.visibility = View.GONE
        tvEmpty.visibility   = View.GONE
    }

    // 좌상단 뒤로가기 버튼이 있는 화면 공통 — 알림 화면과 동일한 "파고들어왔다 빠져나가는" 전환
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.detail_pop_enter, R.anim.detail_pop_exit)
    }
}
