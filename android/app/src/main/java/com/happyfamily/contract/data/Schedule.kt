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
    /** 수정·직접 추가한 일정이면 원래(고정) 키. 날짜를 고쳐도 계획 메모·알림이 따라가도록 */
    val origin: String? = null,
    /** 사용자가 직접 추가한 일정 */
    val custom: Boolean = false,
    /** 사용자가 고친 일정 */
    val edited: Boolean = false,
    /** 계약서에 날짜가 없어 계산한 예상 일정 (묵시적 갱신 가정 등) */
    val estimated: Boolean = false,
) {
    /** 알림·계획·수정 기록을 붙일 때 쓰는 고유 키 */
    val key: String get() = origin ?: "$contractId|$type|$date"

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

/**
 * 일정 고치기 기록. 계약서에서 뽑은 일정을 고치거나 숨기고(kind=edit), 직접 일정을 추가한다(kind=custom).
 * "계약서 내용으로 다시 적용"을 누르면 edit 기록을 지워 계약서에서 뽑은 그대로 돌아간다.
 */
data class ScheduleEdit(
    val id: String,
    /** "edit" 또는 "custom" */
    val kind: String,
    /** kind=edit 일 때 고친 원래 일정의 키 */
    val eventKey: String,
    val hidden: Boolean,
    val type: String,
    val date: String,
    val detail: String,
    val folderId: String?,
    val folderName: String,
) {
    companion object {
        fun from(doc: DocumentSnapshot) = ScheduleEdit(
            id = doc.id,
            kind = doc.getString("kind").orEmpty(),
            eventKey = doc.getString("eventKey").orEmpty(),
            hidden = doc.getBoolean("hidden") ?: false,
            type = doc.getString("type").orEmpty(),
            date = doc.getString("date").orEmpty(),
            detail = doc.getString("detail").orEmpty(),
            folderId = doc.getString("folderId"),
            folderName = doc.getString("folderName").orEmpty(),
        )
    }
}

object ScheduleRepository {
    private val db get() = Firebase.firestore

    private fun uid(): String = AuthRepository.currentUser?.uid ?: error("로그인이 필요합니다.")

    private fun plans() = db.collection("users").document(uid()).collection("plans")

    private fun isValidDate(s: String) = runCatching { LocalDate.parse(s) }.isSuccess

    /**
     * 진행중 폴더(와 아직 폴더가 없는 계약서)의 계약서에서 다음 일정을 뽑는다.
     *  - 임대차는 폴더마다 가장 최근 계약 하나의 만기만 본다 (갱신 전 옛 계약은 제외).
     *    만기가 이미 지났는데 진행중이면 같은 기간으로 묵시적 갱신됐다고 보고 다음 만기를 "예상"으로 계산한다.
     *  - 중도금·잔금·취득 예정일은 오늘 이후인 것만.
     */
    fun events(folders: List<Folder>, contracts: List<Contract>): List<ScheduleEvent> {
        val today = today()
        val byId = folders.associateBy { it.id }
        val out = mutableListOf<ScheduleEvent>()
        val active = contracts.filter { c ->
            c.analysis != null && (c.folderId?.let { byId[it] }?.status ?: FolderStatus.ONGOING) == FolderStatus.ONGOING
        }

        // 1) 임대차 만기: 폴더(없으면 계약서)마다 가장 최근 임대차 하나
        active.filter { it.analysis!!.isLease }
            .groupBy { it.folderId ?: it.id }
            .forEach { (_, leases) ->
                val c = leases.maxBy { (it.analysis!!.leaseEnd.ifBlank { it.analysis.representativeDate }) }
                val a = c.analysis!!
                val folder = c.folderId?.let { byId[it] }
                val name = folder?.name ?: c.title
                val money = if (a.monthlyRent > 0) {
                    "보증금 ${formatWonKr(a.deposit)} / 월 ${formatWonKr(a.monthlyRent)}"
                } else {
                    "보증금 ${formatWonKr(a.deposit)}"
                }
                val parties = "${a.ownerLabel} ${a.lessor.name} / ${a.userLabel} ${a.lessee.name}"
                val end = a.leaseEnd
                when {
                    end.isNotBlank() && isValidDate(end) && end >= today ->
                        out += ScheduleEvent(folder?.id, name, c.id, "임대차 만기", end, "${a.contractType} · $money · $parties")
                    end.isNotBlank() && isValidDate(end) -> {
                        // 만기가 지났는데 진행중 → 묵시적 갱신 가정: 같은 기간을 더해 다음 만기 예상
                        val next = projectRenewal(a.leaseStart, end, today)
                        out += ScheduleEvent(
                            folder?.id, name, c.id, "갱신 만기", next,
                            "원래 만기 ${formatShortDate(end)} 지남 · 묵시적 갱신 가정(확인 필요) · ${a.contractType} · $money · $parties",
                            estimated = true,
                        )
                    }
                }
            }

        // 2) 중도금·잔금·취득 예정
        for (c in active) {
            val a = c.analysis!!
            val folder = c.folderId?.let { byId[it] }
            val name = folder?.name ?: c.title
            fun add(type: String, date: String, detail: String) {
                if (date.isNotBlank() && isValidDate(date) && date >= today) {
                    out += ScheduleEvent(folder?.id, name, c.id, type, date, detail)
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
        return out.distinctBy { "${it.folderId ?: it.contractId}|${it.type}|${it.date}" }.sortedBy { it.date }
    }

    private fun edits() = db.collection("users").document(uid()).collection("scheduleEdits")

    /** 계약서에서 뽑은 일정에 사용자의 수정·숨김·추가를 반영한 최종 일정 (오늘 이후, 날짜순) */
    fun applyEdits(events: List<ScheduleEvent>, editList: List<ScheduleEdit>): List<ScheduleEvent> {
        val today = today()
        val byKey = editList.filter { it.kind == "edit" }.associateBy { it.eventKey }
        val derived = events.mapNotNull { e ->
            val o = byKey[e.key] ?: return@mapNotNull e
            if (o.hidden) return@mapNotNull null
            e.copy(
                type = o.type.ifBlank { e.type },
                date = o.date.takeIf { isValidDate(it) } ?: e.date,
                detail = o.detail.ifBlank { e.detail },
                origin = e.key,
                edited = true,
            )
        }
        val custom = editList.filter { it.kind == "custom" && !it.hidden && isValidDate(it.date) }.map { o ->
            ScheduleEvent(
                folderId = o.folderId, folderName = o.folderName.ifBlank { "직접 추가한 일정" },
                contractId = "custom:${o.id}", type = o.type.ifBlank { "일정" }, date = o.date, detail = o.detail,
                origin = "custom|${o.id}", custom = true,
            )
        }
        return (derived + custom).filter { it.date >= today }.sortedBy { it.date }
    }

    fun editsFlow(): Flow<List<ScheduleEdit>> = callbackFlow {
        val registration = edits().addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.documents.orEmpty().map(ScheduleEdit::from))
        }
        awaitClose { registration.remove() }
    }

    suspend fun allEdits(): List<ScheduleEdit> = edits().get().await().documents.map(ScheduleEdit::from)

    private fun editDocId(eventKey: String) = "edit_" + eventKey.replace("/", "_").take(400)

    /** 일정 고치기 (계약서에서 뽑은 일정이면 고친 기록, 직접 추가한 일정이면 그 내용 자체) */
    suspend fun saveEvent(event: ScheduleEvent, type: String, date: String, detail: String, folderName: String) {
        require(isValidDate(date)) { "날짜를 YYYY-MM-DD 형식으로 입력해 주세요." }
        if (event.custom) {
            edits().document(event.contractId.removePrefix("custom:")).update(
                mapOf("type" to type, "date" to date, "detail" to detail, "folderName" to folderName, "updatedAt" to FieldValue.serverTimestamp()),
            ).await()
        } else {
            edits().document(editDocId(event.key)).set(
                mapOf(
                    "kind" to "edit", "eventKey" to event.key, "hidden" to false,
                    "type" to type, "date" to date, "detail" to detail,
                    "folderId" to event.folderId, "folderName" to folderName,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        }
    }

    /** 일정 지우기 (계약서에서 뽑은 일정은 숨기기 — "다시 적용"으로 되살릴 수 있다) */
    suspend fun deleteEvent(event: ScheduleEvent) {
        if (event.custom) {
            edits().document(event.contractId.removePrefix("custom:")).delete().await()
        } else {
            edits().document(editDocId(event.key)).set(
                mapOf(
                    "kind" to "edit", "eventKey" to event.key, "hidden" to true,
                    "folderId" to event.folderId, "folderName" to event.folderName,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        }
    }

    /** 일정 직접 추가 */
    suspend fun addEvent(folderId: String?, folderName: String, type: String, date: String, detail: String) {
        require(isValidDate(date)) { "날짜를 YYYY-MM-DD 형식으로 입력해 주세요." }
        edits().document().set(
            mapOf(
                "kind" to "custom", "hidden" to false, "type" to type, "date" to date, "detail" to detail,
                "folderId" to folderId, "folderName" to folderName, "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    /** 계약서 내용으로 다시 적용: 고치거나 숨긴 기록을 지운다 (직접 추가한 일정은 남김). 지운 건수를 돌려준다. */
    suspend fun resetEdits(): Int {
        val list = edits().whereEqualTo("kind", "edit").get().await().documents
        list.forEach { it.reference.delete().await() }
        return list.size
    }

    /** 만기가 지난 임대차를 같은 기간(모르면 2년)으로 갱신했다고 보고, 오늘 이후 첫 만기를 돌려준다. */
    fun projectRenewal(start: String, end: String, today: String): String {
        val e = LocalDate.parse(end)
        val s = runCatching { LocalDate.parse(start) }.getOrNull()
        val months = if (s != null && s.isBefore(e)) ChronoUnit.MONTHS.between(s, e.plusDays(1)).coerceIn(6, 120) else 24
        var next = e
        val t = LocalDate.parse(today)
        var guard = 0
        while (next.isBefore(t) && guard++ < 50) next = next.plusMonths(months)
        return next.toString()
    }

    /** 진행중인데 다음 일정(날짜)이 하나도 없는 폴더 — 다음 단계를 정하도록 따로 보여 준다. */
    fun foldersWithoutEvents(folders: List<Folder>, events: List<ScheduleEvent>): List<Folder> {
        val withEvents = events.mapNotNull { it.folderId }.toSet()
        return folders.filter { it.status == FolderStatus.ONGOING && it.id !in withEvents }.sortedBy { it.name }
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
