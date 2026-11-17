package com.happyfamily.contract.data

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * 계약 폴더와 이해관계자.
 *  - users/{uid}/folders/{folderId}        부동산 하나에 대한 계약서 묶음과 계약내용정리
 *  - users/{uid}/stakeholders/{id}         이해관계자와 그 사람 기준 전체 정리 문서
 */
object FolderRepository {
    private val db get() = Firebase.firestore

    private fun uid(): String = AuthRepository.currentUser?.uid ?: error("로그인이 필요합니다.")

    private fun folders() = db.collection("users").document(uid()).collection("folders")
    private fun stakeholders() = db.collection("users").document(uid()).collection("stakeholders")

    fun foldersFlow(): Flow<List<Folder>> = callbackFlow {
        val registration = folders().addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.documents.orEmpty().map(Folder::from))
        }
        awaitClose { registration.remove() }
    }

    fun folderFlow(id: String): Flow<Folder?> = callbackFlow {
        val registration = folders().document(id).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.takeIf { it.exists() }?.let(Folder::from))
        }
        awaitClose { registration.remove() }
    }

    fun stakeholdersFlow(): Flow<List<Stakeholder>> = callbackFlow {
        val registration = stakeholders().addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.documents.orEmpty().map(Stakeholder::from).sortedBy { it.name })
        }
        awaitClose { registration.remove() }
    }

    fun stakeholderFlow(id: String): Flow<Stakeholder?> = callbackFlow {
        val registration = stakeholders().document(id).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.takeIf { it.exists() }?.let(Stakeholder::from))
        }
        awaitClose { registration.remove() }
    }

    suspend fun allFolders(): List<Folder> = folders().get().await().documents.map(Folder::from)

    suspend fun get(id: String): Folder? =
        folders().document(id).get().await().takeIf { it.exists() }?.let(Folder::from)

    suspend fun createFolder(name: String, address: String, stakeholder: String, thumbnail: ByteArray?): String {
        val doc = folders().document()
        doc.set(
            mapOf(
                "name" to name.ifBlank { "새 계약 폴더" }.take(30),
                "address" to address,
                "stakeholder" to stakeholder,
                "status" to FolderStatus.ONGOING.value,
                "summaryText" to "",
                "summaryEntries" to emptyList<Any>(),
                "summaryEdited" to false,
                "summaryState" to "idle",
                "thumbnail" to thumbnail?.let(Blob::fromBytes),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        return doc.id
    }

    private suspend fun update(id: String, fields: Map<String, Any?>) {
        folders().document(id).update(fields + ("updatedAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun rename(id: String, name: String) = update(id, mapOf("name" to name.trim().take(30)))

    /** 진행중/완료를 직접 정한다. null 이면 "자동"(계약 내용으로 판단)으로 되돌린다. */
    suspend fun setStatus(context: Context, id: String, status: FolderStatus?) {
        if (status == null) {
            update(id, mapOf("statusManual" to false))
            SummaryWorker.enqueue(context, id, force = false)
        } else {
            update(id, mapOf("status" to status.value, "statusManual" to true))
        }
    }

    /** 사용자가 직접 고친 정리 글 저장. 이후 새 계약서가 들어와도 자동으로 덮어쓰지 않는다. */
    suspend fun saveSummaryText(id: String, text: String) =
        update(id, mapOf("summaryText" to text, "summaryEdited" to true))

    /** 폴더의 이해관계자를 직접 정한다 (여러 명 가능). 새 이름은 이해관계자 목록에도 추가한다. */
    suspend fun setStakeholders(id: String, names: List<String>) {
        names.forEach { ensureStakeholder(it.trim()) }
        update(id, mapOf("stakeholder" to Stakeholders.join(names), "stakeholderManual" to true))
    }

    /** 등록된 이해관계자 중 이 폴더 계약서의 당사자로 나오는 사람들 */
    private suspend fun matchingStakeholders(folderId: String, registered: List<String>? = null): List<String> {
        val names = registered ?: allStakeholders().map { it.name }
        val analyses = ContractRepository.contractsInFolder(folderId).mapNotNull { it.first.analysis }
        return Stakeholders.matching(names, analyses)
    }

    /**
     * 모든 폴더의 이해관계자를 등록된 이름 기준으로 다시 맞춘다 (이해관계자를 추가·수정·삭제했을 때).
     * [includeManual] 이 false 면 사용자가 직접 고친 폴더는 그대로 둔다. 바뀐 폴더는 정리를 다시 만든다.
     */
    suspend fun reassignAllStakeholders(context: Context, includeManual: Boolean = false): Int {
        val registered = allStakeholders().map { it.name }
        var changed = 0
        for (f in allFolders()) {
            if (f.stakeholderManual && !includeManual) continue
            val names = Stakeholders.join(matchingStakeholders(f.id, registered))
            if (names != f.stakeholder || (includeManual && f.stakeholderManual)) {
                update(f.id, mapOf("stakeholder" to names, "stakeholderManual" to false))
                SummaryWorker.enqueue(context, f.id, force = false)
                changed++
            }
        }
        return changed
    }

    /** 폴더와 그 안의 모든 계약서를 지운다. */
    suspend fun deleteFolder(id: String) {
        ContractRepository.contractsInFolder(id).forEach { (contract, _) -> ContractRepository.delete(contract) }
        folders().document(id).delete().await()
    }

    /**
     * 같은 부동산인데 따로 만들어진 폴더들을 [targetId] 하나로 합친다.
     * 다른 폴더의 계약서를 모두 옮기고 빈 폴더는 지운다. 이름을 주면 바꾼다.
     * 합친 폴더의 정리를 다시 만들도록 예약하고, 사용자가 직접 고친 정리라 건드리지 않았으면 true 를 돌려준다.
     */
    suspend fun mergeFolders(context: Context, targetId: String, otherIds: List<String>, name: String?): Boolean {
        val target = get(targetId) ?: error("합칠 폴더를 찾을 수 없습니다.")
        val others = otherIds.filter { it != targetId }
        for (id in others) {
            val ids = ContractRepository.contractsInFolder(id).map { it.first.id }
            if (ids.isNotEmpty()) ContractRepository.setFolderAll(ids, targetId)
            folders().document(id).delete().await()
        }
        val fields = buildMap<String, Any?> {
            if (!name.isNullOrBlank() && name != target.name) put("name", name.trim().take(30))
            // 주소·사진이 비어 있었으면 합친 폴더의 것으로 채운다.
            if (target.address.isBlank() || target.thumbnail == null) {
                val first = ContractRepository.contractsInFolder(targetId).map { it.first }.firstOrNull { it.analysis != null }
                if (target.address.isBlank() && first?.analysis != null) put("address", first.analysis.propertyAddress)
                if (target.thumbnail == null && first?.thumbnail != null) put("thumbnail", Blob.fromBytes(first.thumbnail))
            }
        }
        if (fields.isNotEmpty()) update(targetId, fields)
        SummaryWorker.enqueue(context, targetId, force = false)
        return target.summaryEdited
    }

    /**
     * 폴더 안에서 고른 계약서들을 새 폴더로 분리한다. 새 폴더 id 를 돌려준다.
     * 이해관계자는 옮긴 계약서의 당사자 중 등록된 사람으로 정하고, 두 폴더 모두 정리를 다시 만들도록 예약한다.
     */
    suspend fun splitContracts(context: Context, sourceId: String, contracts: List<Contract>, name: String): String {
        require(contracts.isNotEmpty()) { "분리할 계약서를 골라 주세요." }
        val first = contracts.firstOrNull { it.analysis != null } ?: contracts.first()
        val newId = createFolder(
            name = name.ifBlank { first.analysis?.folderName.orEmpty() }.ifBlank { "새 계약 폴더" },
            address = first.analysis?.propertyAddress.orEmpty(),
            stakeholder = Stakeholders.join(
                Stakeholders.matching(allStakeholders().map { it.name }, contracts.mapNotNull { it.analysis }),
            ),
            thumbnail = first.thumbnail,
        )
        ContractRepository.setFolderAll(contracts.map { it.id }, newId)
        SummaryWorker.enqueue(context, newId, force = false)
        SummaryWorker.enqueue(context, sourceId, force = false)
        return newId
    }

    /**
     * 계약서들을 [targetId] 폴더로 옮긴다. [targetId] 가 null 이면 [newFolderName] 으로 새 폴더를 만든다.
     * 옮긴 폴더와 원래 폴더의 정리를 다시 만들도록 예약한다. (옮긴 폴더 id, 이름) 을 돌려준다.
     */
    suspend fun moveContracts(
        context: Context,
        contracts: List<Contract>,
        targetId: String?,
        newFolderName: String = "",
    ): Pair<String, String> {
        require(contracts.isNotEmpty()) { "옮길 계약서를 골라 주세요." }
        val first = contracts.firstOrNull { it.analysis != null } ?: contracts.first()
        val (folderId, folderName) = if (targetId != null) {
            targetId to (get(targetId)?.name ?: "")
        } else {
            val name = newFolderName.trim().ifBlank { first.analysis?.folderName.orEmpty() }.ifBlank { "새 계약 폴더" }
            createFolder(
                name = name,
                address = first.analysis?.propertyAddress.orEmpty(),
                stakeholder = Stakeholders.join(
                    Stakeholders.matching(allStakeholders().map { it.name }, contracts.mapNotNull { it.analysis }),
                ),
                thumbnail = first.thumbnail,
            ) to name
        }
        ContractRepository.setFolderAll(contracts.map { it.id }, folderId)
        // 옮기고 나서 비어 버린 원래 폴더는 지운다 (자동으로 만들어졌던 폴더가 남지 않게).
        val oldFolders = contracts.mapNotNull { it.folderId }.distinct().filter { it != folderId }
        val remaining = ContractRepository.allContracts().mapNotNull { it.folderId }.toSet()
        oldFolders.filter { it !in remaining }.forEach { folders().document(it).delete().await() }
        (oldFolders.filter { it in remaining } + folderId).forEach {
            SummaryWorker.enqueue(context, it, force = false, delaySeconds = 3)
        }
        return folderId to folderName
    }

    /**
     * 계약서의 일부 페이지를 [targetId] 폴더(null 이면 [newFolderName] 새 폴더)로 옮긴다.
     * 모든 페이지를 고르면 계약서 전체를 옮긴다. 옮겨 간 폴더 이름을 돌려준다.
     */
    suspend fun movePages(
        context: Context,
        contract: Contract,
        indices: List<Int>,
        targetId: String?,
        newFolderName: String = "",
    ): String {
        if (indices.distinct().size >= contract.pageCount) {
            return moveContracts(context, listOf(contract), targetId, newFolderName).second
        }
        val (folderId, name) = if (targetId != null) {
            targetId to (get(targetId)?.name ?: "")
        } else {
            val n = newFolderName.trim().ifBlank { "새 계약 폴더" }
            // 주소·사진·이해관계자는 옮긴 페이지 분석이 끝나면 채워진다.
            createFolder(name = n, address = "", stakeholder = "", thumbnail = null) to n
        }
        ContractRepository.movePages(context, contract, indices, folderId)
        return name
    }

    // ---------- 이해관계자 ----------

    suspend fun allStakeholders(): List<Stakeholder> =
        stakeholders().get().await().documents.map(Stakeholder::from)

    /** 이름으로 찾고, 없으면 만든다. */
    suspend fun ensureStakeholder(name: String): String {
        val all = allStakeholders()
        all.firstOrNull { it.name == name }?.let { return it.id }
        val doc = stakeholders().document()
        doc.set(
            mapOf(
                "name" to name.take(30),
                "isDefault" to all.isEmpty(),
                "report" to "",
                "reportEdited" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        return doc.id
    }

    suspend fun setDefaultStakeholder(id: String) {
        val batch = db.batch()
        allStakeholders().forEach { s ->
            batch.update(stakeholders().document(s.id), "isDefault", s.id == id)
        }
        batch.commit().await()
    }

    suspend fun deleteStakeholder(context: Context, id: String) {
        val s = stakeholders().document(id).get().await().let(Stakeholder::from)
        stakeholders().document(id).delete().await()
        // 직접 지정한 폴더에서도 이 이름을 뺀다.
        for (f in allFolders()) {
            if (s.name in f.stakeholderNames) {
                update(f.id, mapOf("stakeholder" to Stakeholders.join(f.stakeholderNames - s.name)))
            }
        }
        reassignAllStakeholders(context)
    }

    /** 이해관계자 이름 바꾸기. 폴더에 적힌 이름도 함께 바꾸고 다시 맞춘다. */
    suspend fun renameStakeholder(context: Context, id: String, newName: String) {
        val name = newName.trim().take(30)
        require(name.isNotBlank()) { "이름을 입력해 주세요." }
        val old = stakeholders().document(id).get().await().let(Stakeholder::from)
        stakeholders().document(id).update(mapOf("name" to name, "updatedAt" to FieldValue.serverTimestamp())).await()
        for (f in allFolders()) {
            if (old.name in f.stakeholderNames) {
                update(f.id, mapOf("stakeholder" to Stakeholders.join(f.stakeholderNames.map { if (it == old.name) name else it })))
            }
        }
        reassignAllStakeholders(context)
    }

    suspend fun saveReport(id: String, text: String, edited: Boolean) {
        stakeholders().document(id).update(
            mapOf("report" to text, "reportEdited" to edited, "updatedAt" to FieldValue.serverTimestamp()),
        ).await()
    }

    // ---------- 분석된 계약서를 폴더에 넣기 ----------

    private fun addressKey(address: String) = address.replace(Regex("[\\s,.()\\-]"), "")

    /** 새로 올린 계약서에 맞는 기존 폴더 (AI 가 고른 폴더 → 같은 주소·이름 폴더). 없으면 null = 새 폴더 제안 */
    fun suggestFolder(contract: Contract, matchFolderId: String?, folders: List<Folder>): String? {
        val a = contract.analysis
        folders.firstOrNull { it.id == matchFolderId }?.let { return it.id }
        val addr = a?.propertyAddress.orEmpty()
        if (addr.isNotBlank()) {
            folders.firstOrNull { it.address.isNotBlank() && addressKey(it.address) == addressKey(addr) }?.let { return it.id }
        }
        val name = a?.folderName.orEmpty()
        if (name.isNotBlank()) folders.firstOrNull { it.name == name }?.let { return it.id }
        return null
    }

    /**
     * '제안 폴더로 넣기': 새로 올린 계약서들을 제안된 폴더에 넣는다. 제안이 새 폴더면 그때 만든다.
     * 같은 부동산(주소·이름이 같은) 새 계약서 여러 건은 한 폴더로 모은다. 넣은 폴더 이름들을 돌려준다.
     */
    suspend fun confirmSuggested(context: Context, contracts: List<Contract>): List<String> {
        val folders = allFolders().toMutableList()
        val touched = mutableMapOf<String, String>()
        for (c in contracts.sortedBy { it.createdAt }) {
            if (c.folderId != null) {
                ContractRepository.confirmAll(listOf(c.id))
                touched[c.folderId] = folders.firstOrNull { it.id == c.folderId }?.name.orEmpty()
                continue
            }
            val existing = folders.firstOrNull { it.id == c.suggestedFolderId }?.id ?: suggestFolder(c, null, folders)
            val target = existing ?: run {
                val a = c.analysis
                val name = a?.folderName?.ifBlank { null } ?: a?.propertyAddress?.split(" ")?.take(3)?.joinToString(" ")
                    ?.ifBlank { null } ?: "새 계약 폴더"
                val id = createFolder(
                    name = name,
                    address = a?.propertyAddress.orEmpty(),
                    stakeholder = Stakeholders.join(Stakeholders.matching(allStakeholders().map { it.name }, listOfNotNull(a))),
                    thumbnail = c.thumbnail,
                )
                get(id)?.let { folders += it }
                id
            }
            ContractRepository.setFolderAll(listOf(c.id), target)
            touched[target] = folders.firstOrNull { it.id == target }?.name.orEmpty()
        }
        touched.keys.forEach { SummaryWorker.enqueue(context, it, force = false, delaySeconds = 3) }
        return touched.values.distinct()
    }

    /** 계약서가 하나도 없는 폴더를 지운다 (만든 지 [minAgeMinutes]분 지난 것만 — 막 만들어 넣는 중인 폴더는 제외). */
    suspend fun deleteEmptyFolders(minAgeMinutes: Long = 10): Int {
        val used = ContractRepository.allContracts().mapNotNull { it.folderId }.toSet()
        val cutoff = System.currentTimeMillis() - minAgeMinutes * 60_000
        var n = 0
        for (doc in folders().get().await().documents) {
            if (doc.id in used) continue
            val created = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
            if (created < cutoff) {
                doc.reference.delete().await()
                n++
            }
        }
        return n
    }

    /**
     * 분석이 끝난 계약서를 폴더에 넣는다. 이미 폴더가 정해져 있으면 그대로 두고,
     * 아니면 AI 가 고른 같은 부동산 폴더 → 같은 주소 폴더 → 새 폴더 순서로 정한다.
     */
    suspend fun assignFolder(contract: Contract, matchFolderId: String?, folders: List<Folder>): String {
        val a = contract.analysis
        contract.folderId?.let { id ->
            // 이미 폴더가 정해진 계약서: 폴더에 주소·사진이 비어 있으면 채워 준다 (페이지를 옮겨 만든 새 폴더 등).
            folders.firstOrNull { it.id == id }?.let { f ->
                val fields = buildMap<String, Any?> {
                    if (f.thumbnail == null && contract.thumbnail != null) put("thumbnail", Blob.fromBytes(contract.thumbnail))
                    if (f.address.isBlank() && !a?.propertyAddress.isNullOrBlank()) put("address", a!!.propertyAddress)
                }
                if (fields.isNotEmpty()) update(f.id, fields)
            }
            return id
        }
        val byAi = folders.firstOrNull { it.id == matchFolderId }
        val byAddress = a?.propertyAddress?.takeIf { it.isNotBlank() }?.let { addr ->
            folders.firstOrNull { it.address.isNotBlank() && addressKey(it.address) == addressKey(addr) }
        }
        val folderId = (byAi ?: byAddress)?.id ?: createFolder(
            name = a?.folderName?.ifBlank { null } ?: a?.propertyAddress?.split(" ")?.take(3)?.joinToString(" ").orEmpty(),
            address = a?.propertyAddress.orEmpty(),
            stakeholder = Stakeholders.join(Stakeholders.matching(allStakeholders().map { it.name }, listOfNotNull(a))),
            thumbnail = contract.thumbnail,
        )
        ContractRepository.setFolder(contract.id, folderId)
        (byAi ?: byAddress)?.let { f ->
            // 사진·주소가 없던 폴더면 채워 준다.
            val fields = buildMap<String, Any?> {
                if (f.thumbnail == null && contract.thumbnail != null) put("thumbnail", Blob.fromBytes(contract.thumbnail))
                if (f.address.isBlank() && !a?.propertyAddress.isNullOrBlank()) put("address", a!!.propertyAddress)
            }
            if (fields.isNotEmpty()) update(f.id, fields)
        }
        return folderId
    }

    /** 폴더 기능이 생기기 전에 분석된 계약서들을 폴더에 넣는다 (한 번만 실행되면 됨). */
    suspend fun fileUnfiledContracts(context: Context) {
        val unfiled = ContractRepository.allContracts()
            .filter { it.folderId == null && it.confirmed && it.status == ContractStatus.DONE && it.analysis != null }
        if (unfiled.isEmpty()) return
        val touched = mutableSetOf<String>()
        for (contract in unfiled) {
            val folderId = assignFolder(contract, null, allFolders())
            touched += folderId
        }
        touched.forEach { SummaryWorker.enqueue(context, it, force = false) }
    }

    // ---------- 계약내용정리 ----------

    /**
     * 폴더의 계약내용정리를 다시 만든다. 사진은 쓰지 않고 계약서마다 저장해 둔 핵심 텍스트만 AI 에 보낸다.
     * [force] 가 false 면 사용자가 직접 고친 정리는 건드리지 않는다.
     */
    suspend fun refreshFolderSummary(context: Context, folderId: String, force: Boolean) {
        val folder = get(folderId) ?: return
        if (!force && folder.summaryEdited) return
        update(folderId, mapOf("summaryState" to "running", "summaryError" to FieldValue.delete()))
        try {
            val contracts = ContractRepository.contractsInFolder(folderId)
                .filter { (c, _) -> c.status == ContractStatus.DONE && c.analysis != null }
            if (contracts.isEmpty()) {
                update(
                    folderId,
                    mapOf(
                        "summaryState" to "idle", "summaryText" to "", "summaryEntries" to emptyList<Any>(),
                        "summaryEdited" to false, "summaryNote" to FieldValue.delete(),
                    ),
                )
                return
            }
            // 이해관계자: 등록된 이름 중 계약서 당사자로 나오는 사람만. 직접 고친 폴더는 내용적용(force) 때만 다시 맞춘다.
            val names = if (folder.stakeholderManual && !force) {
                folder.stakeholderNames
            } else {
                Stakeholders.matching(allStakeholders().map { it.name }, contracts.mapNotNull { it.first.analysis })
            }
            var note: String? = null
            val summary = try {
                val result = GeminiAnalyzer.summarize(
                    context, names,
                    listOf(GeminiAnalyzer.SummarizeInput(folder, contracts.map { it.second })),
                )
                result.folders.firstOrNull { it.folderId == folderId } ?: result.folders.firstOrNull()
                    ?: error("AI 정리 결과가 비어 있습니다.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                note = "AI 대신 기본 규칙으로 정리했습니다. ${GeminiAnalyzer.friendlyMessage(e)}"
                LocalSummarizer.summarize(folderId, names, contracts.map { it.first })
            }
            update(
                folderId,
                buildMap {
                    put("summaryEntries", summary.entries.map { it.toMap() })
                    put("summaryText", SummaryText.lines(summary.entries))
                    put("summaryEdited", false)
                    // 직접 정한 진행중/완료는 그대로 둔다.
                    if (!folder.statusManual) put("status", summary.status.value)
                    put("stakeholder", Stakeholders.join(names))
                    if (force) put("stakeholderManual", false)
                    put("summaryState", "idle")
                    put("summaryNote", note ?: FieldValue.delete())
                    put("summaryUpdatedAt", FieldValue.serverTimestamp())
                },
            )
        } catch (e: CancellationException) {
            withContext(NonCancellable) { runCatching { update(folderId, mapOf("summaryState" to "idle")) } }
            throw e
        } catch (e: Exception) {
            runCatching {
                update(folderId, mapOf("summaryState" to "error", "summaryError" to GeminiAnalyzer.friendlyMessage(e)))
            }
        }
    }

    /** 이해관계자와 관련된 폴더: 그 사람이 보관자인 폴더 + 계약서 당사자로 이름이 나오는 폴더 */
    private suspend fun foldersFor(name: String): List<Folder> {
        val all = allFolders()
        val mentioned = ContractRepository.allContracts()
            .filter { c -> c.analysis?.let { Stakeholders.appearsIn(name, it.lessor.name) || Stakeholders.appearsIn(name, it.lessee.name) } == true }
            .mapNotNull { it.folderId }.toSet()
        return all.filter { name in it.stakeholderNames || it.id in mentioned }
    }

    /**
     * 이해관계자 기준 전체 계약내용정리를 다시 만든다 (적용 버튼). 저장해 둔 핵심 텍스트로 한 번에 정리한다.
     * 만든 글을 돌려주고 저장도 한다. AI 를 못 쓰면 기본 규칙으로 정리하고 note 에 이유를 남긴다.
     */
    suspend fun applyStakeholderReport(context: Context, stakeholder: Stakeholder): Pair<String, String?> {
        val name = stakeholder.name
        val folders = foldersFor(name)
        if (folders.isEmpty()) {
            val text = "$name 계약내용정리\n\n계약서 당사자(임대인·임차인 등)로 \"$name\" 이 나오는 계약서가 없습니다."
            saveReport(stakeholder.id, text, edited = false)
            return text to null
        }
        val inputs = folders.map { f ->
            val contracts = ContractRepository.contractsInFolder(f.id)
                .filter { (c, _) -> c.status == ContractStatus.DONE && c.analysis != null }
            f to contracts
        }
        var note: String? = null
        val summaries: Map<String, FolderSummary> = try {
            GeminiAnalyzer.summarize(
                context, listOf(name),
                inputs.map { (f, cs) -> GeminiAnalyzer.SummarizeInput(f, cs.map { it.second }) },
            ).folders.associateBy { it.folderId }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            note = "AI 대신 기본 규칙으로 정리했습니다. ${GeminiAnalyzer.friendlyMessage(e)}"
            emptyMap()
        }
        val groups = inputs.map { (f, cs) ->
            f to (summaries[f.id] ?: LocalSummarizer.summarize(f.id, listOf(name), cs.map { it.first }))
        }
        val text = SummaryText.stakeholderReport(name, groups)
        saveReport(stakeholder.id, text, edited = false)
        return text to note
    }
}
