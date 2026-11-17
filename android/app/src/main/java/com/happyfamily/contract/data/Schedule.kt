package com.happyfamily.contract.data

import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 진행중 계약의 다가오는 일정 (임대차 만기, 잔금일 등) */
data class ScheduleEvent(
    val folderId: String?,
    val folderName: String,
    val contractId: String,
    /** 임대차 만기, 잔금일, 중도금, 취득 예정 */
    val type: String,
    /** YYYY-MM-DD */
    val date: String,
    val detail: String,
) {
    /** 알림·계획을 붙일 때 쓰는 고유 키 */
    val key: String get() = "$contractId|$type|$date"

    val daysLeft: Long get() = ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(date))
}

/** 일정에 붙이는 계획 메모 (재계약 제안, 월세 인상 예정 등) */
data class Plan(
    val id: String,
    val eventKey: String,
    val folderId: String?,
    val tags: List<String>,
    val memo: String,
    val done: Boolean,
) {
    companion object {
        val TAGS = listOf(
            "동일조건 재계약 제안", "월세 인상 예정", "보증금 조정", "신규 세입자 찾기",
            "매도 검토", "직접 입주", "중개사무소 연락", "기타",
        )

        fun from(doc: DocumentSnapshot) = Plan(
            id = doc.id,
            eventKey = doc.getString("eventKey").orEmpty(),
            folderId = doc.getString("folderId"),
            tags = (doc.get("tags") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            memo = doc.getString("memo").orEmpty(),
            done = doc.getBoolean("done") ?: false,
        )
    }
}

object ScheduleRepository {
    private val db get() = Firebase.firestore

    private fun uid(): String = AuthRepository.currentUser?.uid ?: error("로그인이 필요합니다.")

    private fun plans() = db.collection("users").document(uid()).collection("plans")

    private fun isValidDate(s: String) = runCatching { LocalDate.parse(s) }.isSuccess

    /**
     * 진행중 폴더(와 아직 폴더가 없는 계약서)의 계약서에서 오늘 이후 일정을 뽑는다.
     * 이미 끝난(진행중이 아닌) 임대차의 만기는 넣지 않는다.
     */
    fun events(folders: List<Folder>, contracts: List<Contract>): List<ScheduleEvent> {
        val today = today()
        val byId = folders.associateBy { it.id }
        val out = mutableListOf<ScheduleEvent>()
        for (c in contracts) {
            val a = c.analysis ?: continue
            val folder = c.folderId?.let { byId[it] }
            if (folder != null && folder.status != FolderStatus.ONGOING) continue
            val name = folder?.name ?: c.title
            fun add(type: String, date: String, detail: String) {
                if (date.isNotBlank() && isValidDate(date) && date >= today) {
                    out += ScheduleEvent(folder?.id, name, c.id, type, date, detail)
                }
            }
            if (a.isLease) {
                // 폴더 정리에서 끝난 계약으로 판단된 임대차는 건너뛴다.
                val entry = folder?.summaryEntries?.firstOrNull { it.contractId == c.id }
                if (entry == null || entry.ongoing) {
                    val money = if (a.monthlyRent > 0) {
                        "보증금 ${formatWonKr(a.deposit)} / 월 ${formatWonKr(a.monthlyRent)}"
                    } else {
                        "보증금 ${formatWonKr(a.deposit)}"
                    }
                    add("임대차 만기", a.leaseEnd, "${a.contractType} · $money · ${a.ownerLabel} ${a.lessor.name} / ${a.userLabel} ${a.lessee.name}")
                }
            }
            a.middlePayments.forEachIndexed { i, p ->
                add("중도금", p.date, "${if (a.middlePayments.size > 1) "${i + 1}차 " else ""}${formatWonKr(p.amount)}")
            }
            add("잔금일", a.balancePayment.date, formatWonKr(a.balancePayment.amount))
            if (a.contractType == "분양" || a.contractType == "경매") {
                add("취득 예정", a.acquisitionDate, formatWonKr(a.acquisitionAmount))
            }
        }
        return out.distinctBy { it.key }.sortedBy { it.date }
    }

    fun plansFlow(): Flow<List<Plan>> = callbackFlow {
        val registration = plans().addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.documents.orEmpty().map(Plan::from))
        }
        awaitClose { registration.remove() }
    }

    suspend fun savePlan(plan: Plan) {
        val doc = if (plan.id.isBlank()) plans().document() else plans().document(plan.id)
        doc.set(
            mapOf(
                "eventKey" to plan.eventKey,
                "folderId" to plan.folderId,
                "tags" to plan.tags,
                "memo" to plan.memo,
                "done" to plan.done,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    suspend fun deletePlan(id: String) {
        plans().document(id).delete().await()
    }
}
