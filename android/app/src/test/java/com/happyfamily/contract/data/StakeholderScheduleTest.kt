package com.happyfamily.contract.data

import com.happyfamily.contract.ui.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StakeholderScheduleTest {

    @Test
    fun `등록된 이름이 계약서 당사자에 있을 때만 이해관계자`() {
        val analyses = SampleData.gwacheon.mapNotNull { it.analysis }
        assertEquals(listOf("홍길동"), Stakeholders.matching(listOf("홍길동", "김영희"), analyses))
        // 계약서에 없는 이름만 등록돼 있으면 아무도 안 뜬다
        assertEquals(emptyList<String>(), Stakeholders.matching(listOf("김영희"), analyses))
        // 공동명의·띄어쓰기도 찾는다
        val joint = SampleData.analysis("매매", "2020-01-01", "김매도", "홍 길동, 김영희")
        assertEquals(listOf("홍길동", "김영희"), Stakeholders.matching(listOf("홍길동", "김영희"), listOf(joint)))
    }

    @Test
    fun `여러 명 저장과 읽기`() {
        assertEquals("홍길동, 김영희", Stakeholders.join(listOf(" 홍길동", "김영희", "홍길동", "")))
        assertEquals(listOf("홍길동", "김영희"), Stakeholders.split("홍길동, 김영희"))
    }

    @Test
    fun `한눈에 보기는 소재지 계약날짜 계약기간 금액 계약자만`() {
        val a = SampleData.gwacheon[2].analysis!!
        val labels = Glance.items(a).map { it.label }
        assertEquals(listOf("소재지", "계약날짜", "계약기간", "보증금", "월차임", "계약자"), labels)
        assertEquals("21년 05월 01일".length, formatShortDate("2021-05-01").length)
        assertEquals("임대인 홍길동 · 임차인 박월세", Glance.parties(a))
        assertEquals("23년 05월 01일 ~ 27년 04월 30일", Glance.items(a).first { it.label == "계약기간" }.value)
    }

    @Test
    fun `향후 일정은 진행중 폴더의 오늘 이후 만기만`() {
        val future = LocalDate.now().plusMonths(5).toString()
        val past = LocalDate.now().minusMonths(1).toString()
        val ongoing = SampleData.contract(
            "x1",
            SampleData.analysis("월세", "2024-01-01", "홍길동", "박월세", deposit = 10_000_000, rent = 500_000, start = "2024-02-01", end = future),
            folderId = "f1",
        )
        val ended = SampleData.contract(
            "x2",
            SampleData.analysis("전세", "2020-01-01", "홍길동", "이세입", deposit = 300_000_000, start = "2020-02-01", end = past),
            folderId = "f1",
        )
        val folder = SampleData.folder("f1", "과천 미소가", "홍길동", listOf(ongoing, ended))
        val events = ScheduleRepository.events(listOf(folder), listOf(ongoing, ended))
        assertEquals(1, events.size)
        assertEquals("임대차 만기", events[0].type)
        assertEquals(future, events[0].date)
        assertTrue(events[0].daysLeft in 140..160)

        // 완료 폴더의 일정은 안 보인다
        val doneFolder = folder.copy(status = FolderStatus.DONE)
        assertEquals(0, ScheduleRepository.events(listOf(doneFolder), listOf(ongoing)).size)
    }
}
