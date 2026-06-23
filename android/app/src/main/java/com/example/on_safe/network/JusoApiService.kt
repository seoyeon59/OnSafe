package com.example.on_safe.network

import com.example.on_safe.network.dto.JusoResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface JusoApiService {
    @GET("addrlink/addrLinkApi.do")
    suspend fun searchAddress(
        @Query("confmKey") confmKey: String,
        @Query("currentPage") currentPage: Int,
        @Query("countPerPage") countPerPage: Int,
        @Query("keyword") keyword: String,
        @Query("resultType") resultType: String = "json"
    ): Response<JusoResponse>
}