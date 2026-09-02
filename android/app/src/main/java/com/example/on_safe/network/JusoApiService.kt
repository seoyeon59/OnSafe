package com.example.on_safe.network

import com.example.on_safe.network.dto.JusoResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface JusoApiService {

    // confmKey는 API 규격상 쿼리 파라미터로만 전달 가능 — 이 클라이언트에 로깅 인터셉터를 붙이지 말 것
    @GET("addrlink/addrLinkApi.do")
    suspend fun searchAddress(
        @Query("confmKey") confmKey: String,
        @Query("currentPage") currentPage: Int,
        @Query("countPerPage") countPerPage: Int,
        @Query("keyword") keyword: String,
        @Query("resultType") resultType: String = "json"
    ): Response<JusoResponse>
}
