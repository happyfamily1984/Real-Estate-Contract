package com.happyfamily.contract.data

import org.junit.Assert.assertEquals
import org.junit.Test

class KoreanContractParserTest {

    @Test
    fun `금액 읽기`() {
        assertEquals(350_000_000L, KoreanContractParser.parseAmount("금 삼억오천만원정"))
        assertEquals(350_000_000L, KoreanContractParser.parseAmount("3억 5,000만"))
        assertEquals(350_000_000L, KoreanContractParser.parseAmount("350,000,000"))
        assertEquals(50_000_000L, KoreanContractParser.parseAmount("5천만"))
        assertEquals(1_200_000L, KoreanContractParser.parseAmount("일백이십만"))
        assertEquals(520_000_000L, KoreanContractParser.parseAmount("오억이천만원"))
    }

    private val lease = """
        부동산(아파트) 월세 계약서
        1. 부동산의 표시
        소재지 경기도 과천시 별양동 1 미소가아파트 101동 1203호
        2. 계약내용
        보증금 금 오천만원정 (₩50,000,000)
        계약금 금 오백만원정은 계약시에 지불하고
        잔금 금 사천오백만원정은 2023년 5월 1일에 지불한다.
        차임 금 일백이십만원정은 매월 1일에 지불한다.
        제2조 임대인은 위 부동산을 2023년 5월 1일까지 인도하며 임대차 기간은 2023년 5월 1일부터 2027년 4월 30일까지로 한다.
        특약사항
        1. 반려동물 사육을 금지한다.
        2. 도배는 임대인이 해 준다.
        본 계약을 증명하기 위하여 서명 날인한다.
        2023년 4월 1일
        임대인 주소 서울시 성명 홍길동
        임차인 주소 경기도 성명 박월세
    """.trimIndent()

    @Test
    fun `월세 계약서 기본 분석`() {
        val r = KoreanContractParser.parse(lease)
        assertEquals("월세", r["contractType"])
        assertEquals(50_000_000L, r["deposit"])
        assertEquals(1_200_000L, r["monthlyRent"])
        assertEquals("2023-05-01", r["leaseStart"])
        assertEquals("2027-04-30", r["leaseEnd"])
        assertEquals("2023-04-01", r["contractDate"])
        assertEquals("홍길동", (r["lessor"] as Map<*, *>)["name"])
        assertEquals("박월세", (r["lessee"] as Map<*, *>)["name"])
        assertEquals("과천 미소가", r["folderName"])
        assertEquals(listOf("1. 반려동물 사육을 금지한다.", "2. 도배는 임대인이 해 준다."), r["specialTerms"])
        assertEquals("local", r["analysisSource"])
    }

    @Test
    fun `매매 계약서 기본 분석`() {
        val text = """
            부동산 매매계약서
            소재지 서울특별시 양천구 목동 911 건영아파트 5동 402호
            매매대금 금 구억팔천만원정 (₩980,000,000)
            계약금 금 구천팔백만원정
            잔금 금 팔억팔천이백만원정은 2023년 1월 30일에 지불한다.
            2022년 11월 12일
            매도인 성명 홍길동
            매수인 성명 정매수
        """.trimIndent()
        val r = KoreanContractParser.parse(text)
        assertEquals("매매", r["contractType"])
        assertEquals(980_000_000L, r["salePrice"])
        assertEquals(980_000_000L, r["acquisitionAmount"])
        assertEquals("2023-01-30", r["acquisitionDate"])
        assertEquals("2022-11-12", r["contractDate"])
        assertEquals("목동 건영", r["folderName"])
    }

    @Test
    fun `경매 서류는 잔금 납부일과 납부금액이 취득`() {
        val text = """
            매각대금 완납증명원
            사건 2016타경1234 부동산강제경매
            매각대금 금 610,000,000원
            납부금액 금 549,000,000원
            납부일 2016년 10월 5일
            최고가매수인 성명 홍길동
        """.trimIndent()
        val r = KoreanContractParser.parse(text)
        assertEquals("경매", r["contractType"])
        assertEquals(610_000_000L, r["salePrice"])
        assertEquals(549_000_000L, r["acquisitionAmount"])
        assertEquals("2016-10-05", r["acquisitionDate"])
        assertEquals(false, r["acquisitionDateEstimated"])
    }
}
