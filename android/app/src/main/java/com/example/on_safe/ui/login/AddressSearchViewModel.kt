package com.example.on_safe.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.on_safe.network.JusoApiClient
import com.example.on_safe.network.dto.JusoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// 주소 검색 화면 상태 — 팁 안내 / 결과 목록 / 결과 없음(에러 메시지 포함)
sealed class AddressSearchUiState {
    object Tip : AddressSearchUiState()
    data class Results(val list: List<JusoItem>) : AddressSearchUiState()
    data class Empty(val message: String) : AddressSearchUiState()
}

class AddressSearchViewModel : ViewModel() {

    companion object {
        // 검색 API 전용 승인키 (팝업 키 아님)
        // TODO: [보안] 평문 커밋 상태 — 실배포 전 local.properties + BuildConfig로 분리 및 키 재발급 필요.
        //       팀 공용 키가 없어 지금 분리하면 다른 개발자 빌드에서 주소 검색이 막히므로 상수 유지.
        private const val CONFM_KEY = "devU01TX0FVVEgyMDI2MDYwMjAyMDEyODExODk3NTM="
    }

    private val _uiState = MutableLiveData<AddressSearchUiState>(AddressSearchUiState.Tip)
    val uiState: LiveData<AddressSearchUiState> = _uiState

    fun search(keyword: String) {
        // 키 미주입 빌드에서 원인 불명의 검색 실패 대신 명시적 안내
        if (CONFM_KEY.isBlank()) {
            _uiState.value = AddressSearchUiState.Empty(
                "주소 검색을 사용할 수 없습니다.\n관리자에게 문의해주세요."
            )
            return
        }
        viewModelScope.launch {
            try {
                val response = JusoApiClient.api.searchAddress(
                    confmKey     = CONFM_KEY,
                    currentPage  = 1,
                    countPerPage = 20,
                    keyword      = keyword
                )
                val results = response.body()?.results
                _uiState.value = if (response.isSuccessful && results?.common?.errorCode == "0") {
                    val list = results.juso ?: emptyList()
                    if (list.isEmpty()) {
                        AddressSearchUiState.Empty("'$keyword' 검색 결과가 없습니다.\n도로명, 건물명, 지번으로 다시 검색해보세요.")
                    } else {
                        AddressSearchUiState.Results(list)
                    }
                } else {
                    AddressSearchUiState.Empty(results?.common?.errorMessage ?: "검색에 실패했습니다.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = AddressSearchUiState.Empty("네트워크 오류가 발생했습니다.\n연결 상태를 확인해주세요.")
            }
        }
    }
}
