package com.happyfamily.contract.ui

import com.happyfamily.contract.data.ScheduleEdit
import com.happyfamily.contract.data.ScheduleEvent
import com.happyfamily.contract.data.ScheduleRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FolderSortTest {
    private val folders = SampleData.folders // f1 과천 미소가(매수 19년, 최근 23년), f2 목동 건영(경매 16년, 최근 22년)
    private val contracts = SampleData.gwacheon + SampleData.mokdong

    private fun names(mode: SortMode, desc: Boolean) = FolderSort.sort(folders, contracts, mode, desc).map { it.name }

    @Test
    fun `이름순과 거꾸로`() {
        assertEquals(listOf("과천 미소가", "목동 건영"), names(SortMode.NAME, false))
        assertEquals(listOf("목동 건영", "과천 미소가"), names(SortMode.NAME, true))
    }

    @Test
    fun `취득날짜순`() {
        // 목동(16년 경매 취득) → 과천(19년 매수 취득)
        assertEquals(listOf("목동 건영", "과천 미소가"), names(SortMode.ACQUIRED, false))
        assertEquals(listOf("과천 미소가", "목동 건영"), names(SortMode.ACQUIRED, true))
    }

    @Test
    fun `최근계약순`() {
        // 과천(23년 월세) 이 목동(22년 매도) 보다 최근
        assertEquals(listOf("과천 미소가", "목동 건영"), names(SortMode.RECENT, true))
        assertEquals(listOf("목동 건영", "과천 미소가"), names(SortMode.RECENT, false))
    }

    @Test
    fun `일정 고치기 숨기기 추가`() {
        val d1 = LocalDate.now().plusMonths(2).toString()
        val d2 = LocalDate.now().plusMonths(4).toString()
        val e1 = ScheduleEvent("f1", "과천 미소가", "c1", "임대차 만기", d1, "월세")
        val e2 = ScheduleEvent("f1", "과천 미소가", "c2", "잔금일", d1, "잔금")
        val edits = listOf(
            ScheduleEdit("x", "edit", e1.key, false, "임대차 만기", d2, "고친 내용", "f1", "과천 미소가"),
            ScheduleEdit("y", "edit", e2.key, true, "", "", "", "f1", "과천 미소가"),
            ScheduleEdit("z", "custom", "", false, "월세 인상", d1, "5% 인상 통보", null, "목동 건영"),
        )
        val out = ScheduleRepository.applyEdits(listOf(e1, e2), edits)
        assertEquals(listOf("월세 인상", "임대차 만기"), out.map { it.type })
        assertEquals(d2, out[1].date)
        assertEquals(e1.key, out[1].key) // 날짜를 고쳐도 원래 키 유지 (계획 메모·알림 연결)
        assertEquals(true, out[0].custom)
    }
}
