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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.on_safe.R
import com.example.on_safe.network.JusoApiClient
import com.example.on_safe.network.dto.JusoItem
import kotlinx.coroutines.launch

class AddressSearchActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_ZIP     = "zipNo"
        // ⚠️ 검색 API 전용 승인키 (팝업 키 아님!)
        // TODO: [보안] 이 키가 소스코드에 평문으로 들어있고 공개 저장소에 커밋된 상태입니다.
        //       local.properties + BuildConfig로 옮겨서 저장소에는 값이 노출되지 않도록 해야 합니다.
        //       이미 git 히스토리에 노출된 값이므로, 옮기는 것과 별개로 juso.go.kr에서 키 재발급도 검토해주세요.
        private const val CONFM_KEY = "devU01TX0FVVEgyMDI2MDYwMjAyMDEyODExODk3NTM="
    }

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

        // 초기 상태: 팁 카드 표시
        showTip()
    }

    private fun search() {
        val keyword = etKeyword.text.toString().trim()
        if (keyword.isEmpty()) {
            Toast.makeText(this, "검색어를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val response = JusoApiClient.api.searchAddress(
                    confmKey     = CONFM_KEY,
                    currentPage  = 1,
                    countPerPage = 20,
                    keyword      = keyword
                )
                val results = response.body()?.results
                if (response.isSuccessful && results?.common?.errorCode == "0") {
                    val list = results.juso ?: emptyList()
                    if (list.isEmpty()) {
                        showEmpty("'$keyword' 검색 결과가 없습니다.\n도로명, 건물명, 지번으로 다시 검색해보세요.")
                    } else {
                        showResults(list)
                    }
                } else {
                    showEmpty(results?.common?.errorMessage ?: "검색에 실패했습니다.")
                }
            } catch (e: Exception) {
                showEmpty("네트워크 오류가 발생했습니다.\n연결 상태를 확인해주세요.")
            }
        }
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
