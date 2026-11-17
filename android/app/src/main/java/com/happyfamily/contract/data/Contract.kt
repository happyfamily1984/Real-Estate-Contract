package com.happyfamily.contract.data

import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

enum class ContractStatus { PENDING, PROCESSING, DONE, ERROR;

    companion object {
        fun from(value: String?): ContractStatus = when (value) {
            "processing" -> PROCESSING
            "done" -> DONE
            "error" -> ERROR
            else -> PENDING
        }
    }
}

data class Payment(val amount: Long, val date: String)

data class Party(val name: String, val phone: String, val address: String)

data class Agent(
    val officeName: String,
    val representative: String,
    val registrationNumber: String,
    val phone: String,
    val address: String,
)

/** Gemini 가 분석한 결과. GeminiAnalyzer 의 스키마와 같은 구조. */
data class ContractAnalysis(
    /** 매매, 전세, 월세, 분양, 경매, 기타 */
    val contractType: String,
    val propertyType: String,
    val propertyAddress: String,
    val area: String,
    val contractDate: String,
    val leaseStart: String,
    val leaseEnd: String,
    val salePrice: Long,
    val deposit: Long,
    val monthlyRent: Long,
    val managementFee: Long,
    val downPayment: Payment,
    val middlePayments: List<Payment>,
    val balancePayment: Payment,
    val lessor: Party,
    val lessee: Party,
    val agent: Agent,
    val specialTerms: List<String>,
    val summary: String,
    val cautions: List<String>,
    /** 취득일 (경매: 잔금납부일·매각허가결정일 등, 분양·매매: 잔금일 등). 모르면 "" */
    val acquisitionDate: String = "",
    /** 취득일이 확실하지 않아 추정한 값인지 */
    val acquisitionDateEstimated: Boolean = false,
    /** 취득금액 (경매: 잔금납부금액, 분양: 분양금액, 매매: 매매대금) */
    val acquisitionAmount: Long = 0,
    /** AI 가 제안한 짧은 폴더 이름 (예: 과천 미소가) */
    val folderName: String = "",
    /** 계약 핵심 내용 텍스트. 화면에 보이지 않고, 정리 작업에 쓴다. */
    val keyFacts: String = "",
) {
    /** 소유권이 오가는 계약 (매매·분양·경매) */
    val isSale: Boolean get() = contractType == "매매" || contractType == "분양" || contractType == "경매"
    val isLease: Boolean get() = contractType == "전세" || contractType == "월세"

    val ownerLabel: String get() = when (contractType) {
        "분양" -> "분양자"
        "경매" -> "채무자·소유자"
        "매매" -> "매도인"
        else -> "임대인"
    }
    val userLabel: String get() = when (contractType) {
        "분양" -> "수분양자"
        "경매" -> "낙찰자"
        "매매" -> "매수인"
        else -> "임차인"
    }

    /** 정렬·정리에 쓰는 대표 날짜 (YYYY-MM-DD). 경매는 취득일을 우선한다. */
    val representativeDate: String get() = when {
        contractType == "경매" && acquisitionDate.isNotBlank() -> acquisitionDate
        contractDate.isNotBlank() -> contractDate
        else -> acquisitionDate
    }
    val representativeDateEstimated: Boolean get() =
        representativeDate == acquisitionDate && acquisitionDate.isNotBlank() && acquisitionDateEstimated
}

data class Contract(
    val id: String,
    val status: ContractStatus,
    val title: String,
    val contractType: String,
    val pageCount: Int,
    /** 목록에 보여 줄 작은 첫 페이지 사진 (JPEG). */
    val thumbnail: ByteArray?,
    val memo: String,
    val error: String?,
    val createdAt: Date?,
    val analysis: ContractAnalysis?,
    /** 속한 계약 폴더. 분석 전에는 null 일 수 있다. */
    val folderId: String? = null,
    /** 정렬용 날짜 YYYY-MM-DD */
    val sortDate: String = "",
) {
    /** 검색어가 주소, 당사자 이름, 메모 등에 들어 있는지. */
    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val a = analysis
        val haystack = listOfNotNull(
            title, contractType, memo,
            a?.propertyAddress, a?.propertyType, a?.lessor?.name, a?.lessee?.name,
            a?.agent?.officeName, a?.summary,
        ).joinToString(" ")
        return haystack.contains(query.trim(), ignoreCase = true)
    }

    companion object {
        fun from(doc: DocumentSnapshot): Contract {
            @Suppress("UNCHECKED_CAST")
            val analysisMap = doc.get("analysis") as? Map<String, Any?>
            return Contract(
                id = doc.id,
                status = ContractStatus.from(doc.getString("status")),
                title = doc.getString("title").orEmpty(),
                contractType = doc.getString("contractType").orEmpty(),
                pageCount = doc.getLong("pageCount")?.toInt() ?: 0,
                thumbnail = doc.getBlob("thumbnail")?.toBytes(),
                memo = doc.getString("memo").orEmpty(),
                error = doc.getString("error"),
                createdAt = doc.getTimestamp("createdAt")?.toDate(),
                analysis = analysisMap?.let(::parseAnalysis),
                folderId = doc.getString("folderId")?.takeIf { it.isNotBlank() },
                sortDate = doc.getString("sortDate").orEmpty(),
            )
        }

        fun parseAnalysis(m: Map<String, Any?>) = ContractAnalysis(
            contractType = m.str("contractType"),
            propertyType = m.str("propertyType"),
            propertyAddress = m.str("propertyAddress"),
            area = m.str("area"),
            contractDate = m.str("contractDate"),
            leaseStart = m.str("leaseStart"),
            leaseEnd = m.str("leaseEnd"),
            salePrice = m.long("salePrice"),
            deposit = m.long("deposit"),
            monthlyRent = m.long("monthlyRent"),
            managementFee = m.long("managementFee"),
            downPayment = m.map("downPayment").toPayment(),
            middlePayments = m.mapList("middlePayments").map { it.toPayment() },
            balancePayment = m.map("balancePayment").toPayment(),
            lessor = m.map("lessor").toParty(),
            lessee = m.map("lessee").toParty(),
            agent = m.map("agent").let {
                Agent(
                    officeName = it.str("officeName"),
                    representative = it.str("representative"),
                    registrationNumber = it.str("registrationNumber"),
                    phone = it.str("phone"),
                    address = it.str("address"),
                )
            },
            specialTerms = m.strList("specialTerms"),
            summary = m.str("summary"),
            cautions = m.strList("cautions"),
            acquisitionDate = m.str("acquisitionDate"),
            acquisitionDateEstimated = m["acquisitionDateEstimated"] as? Boolean ?: false,
            acquisitionAmount = m.long("acquisitionAmount"),
            folderName = m.str("folderName"),
            keyFacts = m.str("keyFacts"),
        )
    }
}

internal fun Map<String, Any?>.str(key: String) = this[key] as? String ?: ""
internal fun Map<String, Any?>.long(key: String) = (this[key] as? Number)?.toLong() ?: 0L
internal fun Map<String, Any?>.bool(key: String) = this[key] as? Boolean ?: false
internal fun Map<String, Any?>.strList(key: String) =
    (this[key] as? List<*>)?.filterIsInstance<String>().orEmpty()

@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.map(key: String) =
    this[key] as? Map<String, Any?> ?: emptyMap()

@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.mapList(key: String) =
    (this[key] as? List<*>)?.filterIsInstance<Map<String, Any?>>().orEmpty()

private fun Map<String, Any?>.toPayment() = Payment(long("amount"), str("date"))
private fun Map<String, Any?>.toParty() = Party(str("name"), str("phone"), str("address"))
