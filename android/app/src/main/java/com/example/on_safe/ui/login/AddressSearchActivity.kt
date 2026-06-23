package com.example.on_safe.ui.login

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
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
        private const val CONFM_KEY = "devU01TX0FVVEgyMDI2MDYwMjAyMDEyODExODk3NTM="
    }

    private lateinit var etKeyword: EditText
    private lateinit var btnSearch: Button
    private lateinit var btnBack: ImageButton
    private lateinit var rvAddress: RecyclerView
    private lateinit var tvEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_address_search)

        etKeyword = findViewById(R.id.etKeyword)
        btnSearch = findViewById(R.id.btnSearch)
        btnBack   = findViewById(R.id.btnBack)
        rvAddress = findViewById(R.id.rvAddress)
        tvEmpty   = findViewById(R.id.tvEmpty)

        rvAddress.layoutManager = LinearLayoutManager(this)

        btnBack.setOnClickListener { finish() }
        btnSearch.setOnClickListener { search() }
    }

    private fun search() {
        val keyword = etKeyword.text.toString().trim()
        if (keyword.length < 2) {
            Toast.makeText(this, "두 글자 이상 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val response = JusoApiClient.api.searchAddress(
                    confmKey = CONFM_KEY,
                    currentPage = 1,
                    countPerPage = 20,
                    keyword = keyword
                )
                val results = response.body()?.results
                if (response.isSuccessful && results?.common?.errorCode == "0") {
                    val list = results.juso ?: emptyList()
                    if (list.isEmpty()) {
                        showEmpty("검색 결과가 없습니다")
                    } else {
                        showResults(list)
                    }
                } else {
                    showEmpty(results?.common?.errorMessage ?: "검색에 실패했습니다")
                }
            } catch (e: Exception) {
                showEmpty("네트워크 오류가 발생했습니다")
            }
        }
    }

    private fun showResults(list: List<JusoItem>) {
        tvEmpty.visibility = View.GONE
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
        rvAddress.visibility = View.GONE
        tvEmpty.visibility = View.VISIBLE
        tvEmpty.text = msg
    }
}