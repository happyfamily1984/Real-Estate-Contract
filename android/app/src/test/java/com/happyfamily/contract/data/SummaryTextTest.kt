package com.happyfamily.contract.data

import com.happyfamily.contract.ui.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryTextTest {

    @Test
    fun `계약내용정리 한 줄 형식`() {
        val sale = SummaryEntry("c1", "2019-03-02", false, "매매", "매수", 520_000_000, 0, "", "", true, "")
        assertEquals("19년 03월 02일 매매계약 매수 금액 5억 2,000만원 · 현재 진행중", SummaryText.line(sale))

        val jeonse = SummaryEntry("c2", "2021-04-10", false, "전세", "임대", 350_000_000, 0, "2021-05-01", "2023-04-30", false, "")
        assertEquals(
            "21년 04월 10일 전세계약 임대 금액 3억 5,000만원 기간 21년 05월 01일 ~ 23년 04월 30일",
            SummaryText.line(jeonse),
        )

        val monthly = SummaryEntry("c3", "2023-04-01", false, "월세", "임차", 50_000_000, 1_200_000, "2023-05-01", "2027-04-30", true, "")
        assertEquals(
            "23년 04월 01일 월세계약 임차 보증금 5,000만원 월세 120만원 기간 23년 05월 01일 ~ 27년 04월 30일 · 현재 진행중",
            SummaryText.line(monthly),
        )

        val auction = SummaryEntry("m1", "2016-10-05", true, "경매", "매수", 549_000_000, 0, "", "", false, "")
        assertEquals("16년 10월 05일 (예상) 경매낙찰 매수 금액 5억 4,900만원", SummaryText.line(auction))
    }

    @Test
    fun `이해관계자 기준 진행중과 완료`() {
        // 과천: 매수 후 아직 보유, 월세 임대 중 → 진행중
        val g = LocalSummarizer.summarize("f1", listOf("홍길동"), SampleData.gwacheon)
        assertEquals(FolderStatus.ONGOING, g.status)
        assertEquals(listOf("매수", "임대", "임대"), g.entries.map { it.role })
        assertTrue(g.entries[0].ongoing) // 아직 팔지 않음
        assertFalse(g.entries[1].ongoing) // 전세 기간 끝남
        assertTrue(g.entries[2].ongoing) // 월세 기간 중

        // 목동: 경매로 취득 후 매도 → 완료, 경매는 잔금 납부금액·취득일(예상)
        val m = LocalSummarizer.summarize("f2", listOf("홍길동"), SampleData.mokdong)
        assertEquals(FolderStatus.DONE, m.status)
        assertEquals("2016-10-05", m.entries[0].date)
        assertTrue(m.entries[0].dateEstimated)
        assertEquals(549_000_000, m.entries[0].amount)
        assertEquals("매도", m.entries[1].role)
    }

    @Test
    fun `분양은 분양금액이 매수 취득금액`() {
        val a = SampleData.analysis("분양", "2020-06-01", "OO건설", "홍길동", sale = 700_000_000, acqAmount = 700_000_000)
        val s = LocalSummarizer.summarize("f3", listOf("홍길동"), listOf(SampleData.contract("b1", a, "f3")))
        assertEquals("20년 06월 01일 분양계약 매수 금액 7억원 · 현재 진행중", SummaryText.line(s.entries.single()))
    }
}
