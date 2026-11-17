package com.happyfamily.contract.data

import com.google.firebase.firestore.DocumentSnapshot

/** 계약내용정리의 한 줄. AI(또는 기본 규칙)가 만들고, 화면용 글은 [SummaryText] 가 만든다. */
data class SummaryEntry(
    val contractId: String,
    /** YYYY-MM-DD */
    val date: String,
    val dateEstimated: Boolean,
    /** 매매, 전세, 월세, 분양, 경매, 기타 */
    val kind: String,
    /** 이해관계자 입장: 매수, 매도, 임차, 임대, 기타 */
    val role: String,
    /** 매매·분양·경매 금액 또는 전세 보증금 */
    val amount: Long,
    val monthlyRent: Long,
    val periodStart: String,
    val periodEnd: String,
    /** 지금도 이어지고 있는 계약 (보유 중, 임대·임차 중) */
    val ongoing: Boolean,
    val note: String,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "contractId" to contractId, "date" to date, "dateEstimated" to dateEstimated,
        "kind" to kind, "role" to role, "amount" to amount, "monthlyRent" to monthlyRent,
        "periodStart" to periodStart, "periodEnd" to periodEnd, "ongoing" to ongoing, "note" to note,
    )

    companion object {
        fun from(m: Map<String, Any?>) = SummaryEntry(
            contractId = m.str("contractId"), date = m.str("date"), dateEstimated = m.bool("dateEstimated"),
            kind = m.str("kind"), role = m.str("role"), amount = m.long("amount"),
            monthlyRent = m.long("monthlyRent"), periodStart = m.str("periodStart"),
            periodEnd = m.str("periodEnd"), ongoing = m.bool("ongoing"), note = m.str("note"),
        )
    }
}

enum class FolderStatus(val value: String, val label: String) {
    ONGOING("ongoing", "진행중"),
    DONE("done", "완료");

    companion object {
        fun from(value: String?) = if (value == DONE.value) DONE else ONGOING
    }
}

/** 정리 작업 상태 */
enum class SummaryState { IDLE, RUNNING, ERROR;

    companion object {
        fun from(value: String?) = when (value) {
            "running" -> RUNNING
            "error" -> ERROR
            else -> IDLE
        }
    }
}

/** 부동산 하나(같은 주소·호수)에 대한 계약서 묶음. */
data class Folder(
    val id: String,
    val name: String,
    val address: String,
    /** 이 폴더를 보관하는 이해관계자. 진행중/완료와 매수/매도 등은 이 사람 기준. */
    val stakeholder: String,
    val status: FolderStatus,
    val summaryText: String,
    val summaryEntries: List<SummaryEntry>,
    /** 사용자가 정리 글을 직접 고쳤는지. 고쳤으면 새 계약서가 들어와도 자동으로 덮어쓰지 않는다. */
    val summaryEdited: Boolean,
    val summaryState: SummaryState,
    val summaryError: String?,
    val summaryNote: String?,
    val thumbnail: ByteArray?,
) {
    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        return listOf(name, address, stakeholder, summaryText).joinToString(" ")
            .contains(query.trim(), ignoreCase = true)
    }

    companion object {
        fun from(doc: DocumentSnapshot): Folder {
            @Suppress("UNCHECKED_CAST")
            val entries = (doc.get("summaryEntries") as? List<*>)
                ?.filterIsInstance<Map<String, Any?>>()?.map(SummaryEntry::from).orEmpty()
            return Folder(
                id = doc.id,
                name = doc.getString("name").orEmpty(),
                address = doc.getString("address").orEmpty(),
                stakeholder = doc.getString("stakeholder").orEmpty(),
                status = FolderStatus.from(doc.getString("status")),
                summaryText = doc.getString("summaryText").orEmpty(),
                summaryEntries = entries,
                summaryEdited = doc.getBoolean("summaryEdited") ?: false,
                summaryState = SummaryState.from(doc.getString("summaryState")),
                summaryError = doc.getString("summaryError"),
                summaryNote = doc.getString("summaryNote"),
                thumbnail = doc.getBlob("thumbnail")?.toBytes(),
            )
        }
    }
}

/** 이해관계자. 이 사람 기준으로 모든 계약을 정리한 문서(report)를 가진다. */
data class Stakeholder(
    val id: String,
    val name: String,
    val isDefault: Boolean,
    val report: String,
    val reportEdited: Boolean,
) {
    companion object {
        fun from(doc: DocumentSnapshot) = Stakeholder(
            id = doc.id,
            name = doc.getString("name").orEmpty(),
            isDefault = doc.getBoolean("isDefault") ?: false,
            report = doc.getString("report").orEmpty(),
            reportEdited = doc.getBoolean("reportEdited") ?: false,
        )
    }
}
