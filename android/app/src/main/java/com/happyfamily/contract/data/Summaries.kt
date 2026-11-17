package com.happyfamily.contract.data

import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

private val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

/** 350000000 → "3억 5,000만원", 1500000 → "150만원", 0 → "-" */
fun formatWonKr(amount: Long): String {
    if (amount <= 0L) return "-"
    val eok = amount / 100_000_000
    val man = (amount % 100_000_000) / 10_000
    val won = amount % 10_000
    val parts = buildList {
        if (eok > 0) add("${numberFormat.format(eok)}억")
        if (man > 0) add("${numberFormat.format(man)}만")
        if (won > 0) add(numberFormat.format(won))
    }
    return parts.joinToString(" ") + "원"
}

/** "2021-05-01" → "21년 05월 01일" (정리 문서 형식) */
fun formatShortDate(value: String): String {
    val p = value.split("-")
    if (p.size != 3 || p[0].length != 4) return value.ifBlank { "날짜 미상" }
    return "${p[0].takeLast(2)}년 ${p[1].padStart(2, '0')}월 ${p[2].padStart(2, '0')}일"
}

fun today(): String = LocalDate.now().toString()

/** 계약내용정리 글 만들기 */
object SummaryText {
    const val ONGOING_MARK = "현재 진행중"
    const val ESTIMATED_MARK = "(예상)"

    fun kindLabel(kind: String) = when (kind) {
        "매매" -> "매매계약"
        "전세" -> "전세계약"
        "월세" -> "월세계약"
        "분양" -> "분양계약"
        "경매" -> "경매낙찰"
        else -> "기타계약"
    }

    /** 예: 21년 05월 01일 전세계약 임차 금액 3억원 기간 21년 05월 01일 ~ 23년 04월 30일 · 현재 진행중 */
    fun line(e: SummaryEntry): String = buildString {
        append(formatShortDate(e.date))
        if (e.dateEstimated) append(" ").append(ESTIMATED_MARK)
        append(" ").append(kindLabel(e.kind))
        if (e.role.isNotBlank() && e.role != "기타") append(" ").append(e.role)
        when (e.kind) {
            "월세" -> {
                append(" 보증금 ").append(formatWonKr(e.amount))
                append(" 월세 ").append(formatWonKr(e.monthlyRent))
            }
            else -> if (e.amount > 0) append(" 금액 ").append(formatWonKr(e.amount))
        }
        if (e.periodStart.isNotBlank() || e.periodEnd.isNotBlank()) {
            append(" 기간 ").append(formatShortDate(e.periodStart))
            append(" ~ ").append(formatShortDate(e.periodEnd))
        }
        if (e.ongoing) append(" · ").append(ONGOING_MARK)
        if (e.note.isNotBlank()) append(" (").append(e.note.trim()).append(")")
    }

    /** 같은 서류가 두 번 올라가 똑같은 줄이 생기면 한 줄만 남긴다. */
    fun lines(entries: List<SummaryEntry>): String =
        entries.sortedBy { it.date.ifBlank { "9999" } }.map { line(it) }.distinct().joinToString("\n")

    /** 폴더 정리를 파일·클립보드로 내보낼 때의 전체 문서 */
    fun folderDocument(folder: Folder): String = buildString {
        appendLine("${folder.name} 계약내용정리")
        val info = listOfNotNull(
            folder.address.takeIf { it.isNotBlank() },
            folder.stakeholderNames.takeIf { it.isNotEmpty() }?.let { "이해관계자 ${it.joinToString(", ")}" },
            folder.status.label,
        )
        appendLine(info.joinToString(" · "))
        appendLine()
        append(folder.summaryText)
    }

    /** 이해관계자 기준 전체 정리 문서 */
    fun stakeholderReport(name: String, groups: List<Pair<Folder, FolderSummary>>): String = buildString {
        val ongoing = groups.count { it.second.status == FolderStatus.ONGOING }
        appendLine("$name 계약내용정리")
        appendLine("${formatShortDate(today())} 기준 · 진행중 ${ongoing}건 · 완료 ${groups.size - ongoing}건")
        val sorted = groups.sortedWith(
            compareBy<Pair<Folder, FolderSummary>> { it.second.status != FolderStatus.ONGOING }
                .thenBy { g -> g.second.entries.minOfOrNull { it.date.ifBlank { "9999" } } ?: "9999" },
        )
        for ((folder, summary) in sorted) {
            appendLine()
            append("[").append(summary.status.label).append("] ").append(folder.name)
            if (folder.address.isNotBlank()) append(" (").append(folder.address).append(")")
            appendLine()
            appendLine(lines(summary.entries).ifBlank { "정리된 계약이 없습니다." })
        }
    }.trimEnd()

    /**
     * AI 에게 넘길 계약 핵심 내용 텍스트. 계약서를 올릴 때 한 번 만들어 Firestore 에 숨겨 두고,
     * 정리할 때는 사진 대신 이 텍스트만 쓴다.
     */
    fun digest(contractId: String, a: ContractAnalysis): String = buildString {
        append("[계약서 ").append(contractId).append("] ")
        append("종류: ").append(a.contractType)
        append(" / 계약일: ").append(a.contractDate.ifBlank { "미상" })
        if (a.acquisitionDate.isNotBlank()) {
            append(" / 취득일: ").append(a.acquisitionDate)
            if (a.acquisitionDateEstimated) append("(추정)")
        }
        if (a.acquisitionAmount > 0) append(" / 취득금액: ").append(a.acquisitionAmount)
        append(" / 주소: ").append(a.propertyAddress)
        append(" / ").append(a.ownerLabel).append(": ").append(a.lessor.name.ifBlank { "미상" })
        append(" / ").append(a.userLabel).append(": ").append(a.lessee.name.ifBlank { "미상" })
        if (a.salePrice > 0) append(" / 매매·분양대금: ").append(a.salePrice)
        if (a.deposit > 0) append(" / 보증금: ").append(a.deposit)
        if (a.monthlyRent > 0) append(" / 월세: ").append(a.monthlyRent)
        if (a.leaseStart.isNotBlank() || a.leaseEnd.isNotBlank()) {
            append(" / 기간: ").append(a.leaseStart).append(" ~ ").append(a.leaseEnd)
        }
        if (a.balancePayment.amount > 0 || a.balancePayment.date.isNotBlank()) {
            append(" / 잔금: ").append(a.balancePayment.amount).append(" ").append(a.balancePayment.date)
        }
        if (a.specialTerms.isNotEmpty()) append(" / 특약 ").append(a.specialTerms.size).append("건")
        if (a.keyFacts.isNotBlank()) append("\n핵심: ").append(a.keyFacts.trim())
    }
}

/** 한 폴더를 이해관계자 기준으로 정리한 결과 */
data class FolderSummary(
    val folderId: String,
    val status: FolderStatus,
    val entries: List<SummaryEntry>,
)

/**
 * AI 를 쓸 수 없을 때(무료 사용량 초과 등) 쓰는 기본 규칙 정리.
 * 이름이 당사자 이름에 들어 있는지로 매수/매도, 임차/임대를 정한다.
 */
object LocalSummarizer {
    fun summarize(folderId: String, stakeholders: List<String>, contracts: List<Contract>): FolderSummary {
        val now = today()
        val analyzed = contracts.filter { it.analysis != null }
            .sortedBy { it.analysis!!.representativeDate.ifBlank { "9999" } }

        val entries = analyzed.map { c ->
            val a = c.analysis!!
            val isUser = stakeholders.any { Stakeholders.appearsIn(it, a.lessee.name) }
            val isOwner = stakeholders.any { Stakeholders.appearsIn(it, a.lessor.name) }
            val role = when {
                a.isSale && isUser -> "매수"
                a.isSale && isOwner -> "매도"
                a.isLease && isUser -> "임차"
                a.isLease && isOwner -> "임대"
                else -> ""
            }
            SummaryEntry(
                contractId = c.id,
                date = a.representativeDate,
                dateEstimated = a.representativeDateEstimated,
                kind = a.contractType.ifBlank { "기타" },
                role = role,
                amount = when {
                    a.isSale -> if (a.acquisitionAmount > 0) a.acquisitionAmount else a.salePrice
                    else -> a.deposit
                },
                monthlyRent = a.monthlyRent,
                periodStart = if (a.isLease) a.leaseStart else "",
                periodEnd = if (a.isLease) a.leaseEnd else "",
                ongoing = false,
                note = "",
            )
        }

        // 마지막 매수 이후 매도가 없으면 보유 중, 임대차는 종료일이 지나지 않았으면 진행 중
        // (이해관계자가 없으면 역할을 모르므로 임대차 기간만 본다)
        val lastBuy = entries.indexOfLast { it.role == "매수" }
        val soldAfter = lastBuy >= 0 && entries.drop(lastBuy + 1).any { it.role == "매도" }
        val marked = entries.mapIndexed { i, e ->
            val ongoing = when {
                e.role == "매수" -> i == lastBuy && !soldAfter
                e.kind == "전세" || e.kind == "월세" ->
                    (e.role == "임차" || e.role == "임대" || stakeholders.isEmpty()) &&
                        (e.periodEnd.isBlank() || e.periodEnd >= now)
                else -> false
            }
            e.copy(ongoing = ongoing)
        }
        val status = when {
            marked.any { it.ongoing } -> FolderStatus.ONGOING
            marked.isEmpty() -> FolderStatus.ONGOING
            else -> FolderStatus.DONE
        }
        return FolderSummary(folderId, status, marked)
    }
}

/** 계약서 한눈에 보기: 소재지, 계약날짜, 계약기간, 금액, 계약자 (이것만) */
object Glance {
    data class Item(val label: String, val value: String)

    fun items(a: ContractAnalysis): List<Item> = buildList {
        if (a.propertyAddress.isNotBlank()) add(Item("소재지", a.propertyAddress))
        if (a.contractDate.isNotBlank()) add(Item("계약날짜", formatShortDate(a.contractDate)))
        if (a.contractType == "경매" && a.acquisitionDate.isNotBlank()) {
            add(Item("취득일", formatShortDate(a.acquisitionDate) + if (a.acquisitionDateEstimated) " ${SummaryText.ESTIMATED_MARK}" else ""))
        }
        if (a.leaseStart.isNotBlank() || a.leaseEnd.isNotBlank()) {
            add(Item("계약기간", "${formatShortDate(a.leaseStart)} ~ ${formatShortDate(a.leaseEnd)}"))
        }
        amounts(a).forEach { (label, value) -> add(Item(label, value)) }
        parties(a)?.let { add(Item("계약자", it)) }
    }

    /** 금액 정보: 매매대금·분양금액·매각대금, 취득금액, 보증금, 월차임 */
    fun amounts(a: ContractAnalysis): List<Pair<String, String>> = buildList {
        if (a.salePrice > 0) {
            val label = when (a.contractType) {
                "분양" -> "분양금액"
                "경매" -> "매각대금"
                else -> "매매대금"
            }
            add(label to formatWonKr(a.salePrice))
        }
        if (a.acquisitionAmount > 0 && a.acquisitionAmount != a.salePrice) add("취득금액" to formatWonKr(a.acquisitionAmount))
        if (a.deposit > 0) add("보증금" to formatWonKr(a.deposit))
        if (a.monthlyRent > 0) add("월차임" to formatWonKr(a.monthlyRent))
    }

    /** "임대인 홍길동 · 임차인 박월세" */
    fun parties(a: ContractAnalysis): String? = listOfNotNull(
        a.lessor.name.takeIf { it.isNotBlank() }?.let { "${a.ownerLabel} $it" },
        a.lessee.name.takeIf { it.isNotBlank() }?.let { "${a.userLabel} $it" },
    ).joinToString(" · ").ifBlank { null }
}
