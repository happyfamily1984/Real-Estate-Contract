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

    suspend fun setStatus(id: String, status: FolderStatus) = update(id, mapOf("status" to status.value))

    /** 사용자가 직접 고친 정리 글 저장. 이후 새 계약서가 들어와도 자동으로 덮어쓰지 않는다. */
    suspend fun saveSummaryText(id: String, text: String) =
        update(id, mapOf("summaryText" to text, "summaryEdited" to true))

    suspend fun setStakeholder(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotBlank()) ensureStakeholder(trimmed)
        update(id, mapOf("stakeholder" to trimmed))
    }

    /** 폴더와 그 안의 모든 계약서를 지운다. */
    suspend fun deleteFolder(id: String) {
        ContractRepository.contractsInFolder(id).forEach { (contract, _) -> ContractRepository.delete(contract) }
        folders().document(id).delete().await()
    }

    // ---------- 이해관계자 ----------

    suspend fun allStakeholders(): List<Stakeholder> =
        stakeholders().get().await().documents.map(Stakeholder::from)

    /** 이름으로 찾고, 없으면 만든다. 첫 이해관계자는 기본(본인)으로 정한다. */
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

    suspend fun defaultStakeholderName(): String =
        allStakeholders().let { list -> (list.firstOrNull { it.isDefault } ?: list.firstOrNull())?.name.orEmpty() }

    suspend fun setDefaultStakeholder(id: String) {
        val batch = db.batch()
        allStakeholders().forEach { s ->
            batch.update(stakeholders().document(s.id), "isDefault", s.id == id)
        }
        batch.commit().await()
    }

    suspend fun deleteStakeholder(id: String) {
        stakeholders().document(id).delete().await()
    }

    suspend fun saveReport(id: String, text: String, edited: Boolean) {
        stakeholders().document(id).update(
            mapOf("report" to text, "reportEdited" to edited, "updatedAt" to FieldValue.serverTimestamp()),
        ).await()
    }

    // ---------- 분석된 계약서를 폴더에 넣기 ----------

    private fun addressKey(address: String) = address.replace(Regex("[\\s,.()\\-]"), "")

    /**
     * 분석이 끝난 계약서를 폴더에 넣는다. 이미 폴더가 정해져 있으면 그대로 두고,
     * 아니면 AI 가 고른 같은 부동산 폴더 → 같은 주소 폴더 → 새 폴더 순서로 정한다.
     */
    suspend fun assignFolder(contract: Contract, matchFolderId: String?, folders: List<Folder>): String {
        contract.folderId?.let { return it }
        val a = contract.analysis
        val byAi = folders.firstOrNull { it.id == matchFolderId }
        val byAddress = a?.propertyAddress?.takeIf { it.isNotBlank() }?.let { addr ->
            folders.firstOrNull { it.address.isNotBlank() && addressKey(it.address) == addressKey(addr) }
        }
        val folderId = (byAi ?: byAddress)?.id ?: createFolder(
            name = a?.folderName?.ifBlank { null } ?: a?.propertyAddress?.split(" ")?.take(3)?.joinToString(" ").orEmpty(),
            address = a?.propertyAddress.orEmpty(),
            stakeholder = defaultStakeholderName(),
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
            .filter { it.folderId == null && it.status == ContractStatus.DONE && it.analysis != null }
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
            var stakeholder = folder.stakeholder
            var note: String? = null
            val summary = try {
                val result = GeminiAnalyzer.summarize(
                    context, stakeholder,
                    listOf(GeminiAnalyzer.SummarizeInput(folder, contracts.map { it.second })),
                )
                if (stakeholder.isBlank() && result.inferredStakeholder.isNotBlank()) {
                    stakeholder = result.inferredStakeholder.trim()
                }
                result.folders.firstOrNull { it.folderId == folderId } ?: result.folders.firstOrNull()
                    ?: error("AI 정리 결과가 비어 있습니다.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                note = "AI 대신 기본 규칙으로 정리했습니다. ${GeminiAnalyzer.friendlyMessage(e)}"
                LocalSummarizer.summarize(folderId, stakeholder, contracts.map { it.first })
            }
            if (stakeholder != folder.stakeholder && stakeholder.isNotBlank()) ensureStakeholder(stakeholder)
            update(
                folderId,
                mapOf(
                    "summaryEntries" to summary.entries.map { it.toMap() },
                    "summaryText" to SummaryText.lines(summary.entries),
                    "summaryEdited" to false,
                    "status" to summary.status.value,
                    "stakeholder" to stakeholder,
                    "summaryState" to "idle",
                    "summaryNote" to (note ?: FieldValue.delete()),
                    "summaryUpdatedAt" to FieldValue.serverTimestamp(),
                ),
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
            .filter { c -> c.analysis?.let { it.lessor.name.contains(name) || it.lessee.name.contains(name) } == true }
            .mapNotNull { it.folderId }.toSet()
        return all.filter { it.stakeholder == name || it.id in mentioned }
    }

    /**
     * 이해관계자 기준 전체 계약내용정리를 다시 만든다 (적용 버튼). 저장해 둔 핵심 텍스트로 한 번에 정리한다.
     * 만든 글을 돌려주고 저장도 한다. AI 를 못 쓰면 기본 규칙으로 정리하고 note 에 이유를 남긴다.
     */
    suspend fun applyStakeholderReport(context: Context, stakeholder: Stakeholder): Pair<String, String?> {
        val name = stakeholder.name
        val folders = foldersFor(name)
        if (folders.isEmpty()) {
            val text = "$name 계약내용정리\n\n이 이해관계자와 관련된 계약서가 없습니다.\n폴더에서 이해관계자를 \"$name\" 으로 지정해 주세요."
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
                context, name,
                inputs.map { (f, cs) -> GeminiAnalyzer.SummarizeInput(f, cs.map { it.second }) },
            ).folders.associateBy { it.folderId }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            note = "AI 대신 기본 규칙으로 정리했습니다. ${GeminiAnalyzer.friendlyMessage(e)}"
            emptyMap()
        }
        val groups = inputs.map { (f, cs) ->
            f to (summaries[f.id] ?: LocalSummarizer.summarize(f.id, name, cs.map { it.first }))
        }
        val text = SummaryText.stakeholderReport(name, groups)
        saveReport(stakeholder.id, text, edited = false)
        return text to note
    }
}
