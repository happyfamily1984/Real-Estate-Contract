package com.happyfamily.contract.ui

import com.happyfamily.contract.data.Agent
import com.happyfamily.contract.data.Contract
import com.happyfamily.contract.data.ContractAnalysis
import com.happyfamily.contract.data.ContractStatus
import com.happyfamily.contract.data.Folder
import com.happyfamily.contract.data.FolderStatus
import com.happyfamily.contract.data.LocalSummarizer
import com.happyfamily.contract.data.Party
import com.happyfamily.contract.data.Payment
import com.happyfamily.contract.data.Stakeholder
import com.happyfamily.contract.data.SummaryState
import com.happyfamily.contract.data.SummaryText
import java.util.Date

/** 화면·규칙 테스트용 예시 데이터 */
object SampleData {
    fun analysis(
        type: String,
        date: String,
        lessor: String,
        lessee: String,
        sale: Long = 0,
        deposit: Long = 0,
        rent: Long = 0,
        start: String = "",
        end: String = "",
        acqDate: String = "",
        acqEstimated: Boolean = false,
        acqAmount: Long = 0,
    ) = ContractAnalysis(
        contractType = type, propertyType = "아파트", propertyAddress = "경기도 과천시 별양동 미소가 101동 1203호", area = "",
        contractDate = date, leaseStart = start, leaseEnd = end, salePrice = sale,
        deposit = deposit, monthlyRent = rent, managementFee = 0,
        downPayment = Payment(0, ""), middlePayments = emptyList(), balancePayment = Payment(0, ""),
        lessor = Party(lessor, "", ""), lessee = Party(lessee, "", ""), agent = Agent("", "", "", "", ""),
        specialTerms = emptyList(), summary = "", cautions = emptyList(),
        acquisitionDate = acqDate, acquisitionDateEstimated = acqEstimated, acquisitionAmount = acqAmount,
        folderName = "과천 미소가",
    )

    fun contract(id: String, a: ContractAnalysis?, folderId: String? = "f1", status: ContractStatus = ContractStatus.DONE) =
        Contract(
            id = id, status = status, title = a?.propertyAddress ?: "분석 중인 계약서", contractType = a?.contractType.orEmpty(),
            pageCount = 2, thumbnail = null, memo = "", error = null, createdAt = Date(), analysis = a,
            folderId = folderId, sortDate = a?.representativeDate.orEmpty(),
        )

    /** 홍길동: 19년 매수 → 21년 전세 놓음(임대) → 23년 월세 갱신 */
    val gwacheon = listOf(
        contract("c1", analysis("매매", "2019-03-02", "김매도", "홍길동", sale = 520_000_000, acqDate = "2019-05-30", acqAmount = 520_000_000)),
        contract("c2", analysis("전세", "2021-04-10", "홍길동", "이세입", deposit = 350_000_000, start = "2021-05-01", end = "2023-04-30")),
        contract("c3", analysis("월세", "2023-04-01", "홍길동", "박월세", deposit = 50_000_000, rent = 1_200_000, start = "2023-05-01", end = "2027-04-30")),
    )

    /** 홍길동: 경매 낙찰(취득일 추정) → 매도 */
    val mokdong = listOf(
        contract("m1", analysis("경매", "2016-08-20", "최채무", "홍길동", sale = 610_000_000, acqDate = "2016-10-05", acqEstimated = true, acqAmount = 549_000_000), "f2"),
        contract("m2", analysis("매매", "2022-11-12", "홍길동", "정매수", sale = 980_000_000), "f2"),
    )

    fun folder(id: String, name: String, stakeholder: String, contracts: List<Contract>): Folder {
        val s = LocalSummarizer.summarize(id, listOf(stakeholder), contracts)
        return Folder(
            id = id, name = name, address = contracts.first().analysis!!.propertyAddress, stakeholder = stakeholder,
            status = s.status, summaryText = SummaryText.lines(s.entries), summaryEntries = s.entries,
            summaryEdited = false, summaryState = SummaryState.IDLE, summaryError = null, summaryNote = null, thumbnail = null,
        )
    }

    val folders = listOf(
        folder("f1", "과천 미소가", "홍길동", gwacheon),
        folder("f2", "목동 건영", "홍길동", mokdong).copy(address = "서울특별시 양천구 목동 건영아파트 5동 402호"),
    )

    val stakeholders = listOf(Stakeholder("s1", "홍길동", true, "", false), Stakeholder("s2", "김영희", false, "", false))

    fun homeState() = HomeState(
        unfiled = listOf(contract("u1", null, folderId = null, status = ContractStatus.PROCESSING)),
        ongoing = folders.filter { it.status == FolderStatus.ONGOING },
        done = folders.filter { it.status == FolderStatus.DONE },
        contractCounts = mapOf("f1" to 3, "f2" to 2),
        stakeholders = stakeholders,
        isEmpty = false,
    )
}
