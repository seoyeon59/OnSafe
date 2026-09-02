package com.example.on_safe.network.dto

/*
 * 도로명주소 API 응답 — JusoApiClient는 기본 Gson(camelCase 그대로)을 쓰므로
 * 프로퍼티명이 API 응답 키와 정확히 일치해야 한다. ApiClient의 snake_case 정책이 적용되지 않는다.
 */

data class JusoResponse(
    val results: JusoResults
)

data class JusoResults(
    val common: JusoCommon,
    // 검색 결과 없음이면 juso 키 자체가 없음
    val juso: List<JusoItem>?
)

data class JusoCommon(
    val errorCode: String,
    val errorMessage: String,
    val totalCount: String
)

data class JusoItem(
    val roadAddr: String,        // 전체 도로명주소
    val roadAddrPart1: String,   // 도로명주소(참고항목 제외)
    val roadAddrPart2: String?,  // 참고항목
    val jibunAddr: String,       // 지번주소
    val zipNo: String,           // 우편번호
    val siNm: String,            // 시도명
    val sggNm: String,           // 시군구명
    val emdNm: String            // 읍면동명
)
