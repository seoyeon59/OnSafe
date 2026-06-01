package com.example.on_safe.network.dto

data class JusoResponse(
    val results: JusoResults
)

data class JusoResults(
    val common: JusoCommon,
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