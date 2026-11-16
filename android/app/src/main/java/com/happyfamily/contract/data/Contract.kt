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
) {
    val isSale: Boolean get() = contractType == "매매"
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
            )
        }

        private fun parseAnalysis(m: Map<String, Any?>) = ContractAnalysis(
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
        )

        private fun Map<String, Any?>.str(key: String) = this[key] as? String ?: ""
        private fun Map<String, Any?>.long(key: String) = (this[key] as? Number)?.toLong() ?: 0L
        private fun Map<String, Any?>.strList(key: String) =
            (this[key] as? List<*>)?.filterIsInstance<String>().orEmpty()

        @Suppress("UNCHECKED_CAST")
        private fun Map<String, Any?>.map(key: String) =
            this[key] as? Map<String, Any?> ?: emptyMap()

        @Suppress("UNCHECKED_CAST")
        private fun Map<String, Any?>.mapList(key: String) =
            (this[key] as? List<*>)?.filterIsInstance<Map<String, Any?>>().orEmpty()

        private fun Map<String, Any?>.toPayment() = Payment(long("amount"), str("date"))
        private fun Map<String, Any?>.toParty() = Party(str("name"), str("phone"), str("address"))
    }
}
