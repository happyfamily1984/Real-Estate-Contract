package com.happyfamily.contract.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import com.google.firebase.Firebase
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * 저장 구조 (모두 Firestore — Firebase 무료 요금제로 동작)
 *  - users/{uid}/folders/{folderId}                부동산별 계약 폴더 (FolderRepository)
 *  - users/{uid}/contracts/{contractId}            계약서 정보, 분석 결과, 썸네일
 *  - users/{uid}/contracts/{contractId}/pages/{n}  페이지 사진 (JPEG, 문서 1개 1MB 한도 안으로 압축)
 *
 * 사진을 저장하고 status = "pending" 으로 만든 뒤 [AnalyzeWorker] 가 Gemini 로 분석한다.
 */
/** 같은 파일이 이미 있어서 올리거나 옮기지 않았을 때 */
class DuplicateContractException(message: String) : Exception(message)

object ContractRepository {
    /** 계약서 한 건의 최대 페이지 수 */
    const val MAX_PAGES = 50

    /** 한 번에 올릴 수 있는 파일 수와 전체 크기 */
    const val MAX_FILES = 100
    const val MAX_TOTAL_BYTES = 100L * 1024 * 1024

    // Firestore 문서 1개는 1MiB 까지라 그보다 충분히 작게 줄인다.
    private const val MAX_PAGE_BYTES = 700_000
    private const val THUMBNAIL_EDGE = 240
    private const val PDF_RENDER_EDGE = 2000

    private val db get() = Firebase.firestore

    private fun uid(): String = AuthRepository.currentUser?.uid ?: error("로그인이 필요합니다.")

    private fun contracts(uid: String) = db.collection("users").document(uid).collection("contracts")

    private fun pages(uid: String, contractId: String) =
        contracts(uid).document(contractId).collection("pages")

    fun contractsFlow(): Flow<List<Contract>> = callbackFlow {
        val registration = contracts(uid())
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty().map(Contract::from))
            }
        awaitClose { registration.remove() }
    }

    fun contractFlow(id: String): Flow<Contract?> = callbackFlow {
        val registration = contracts(uid()).document(id).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.takeIf { it.exists() }?.let(Contract::from))
        }
        awaitClose { registration.remove() }
    }

    /** 사진(JPG 등) 한 장 또는 PDF 의 한 페이지 */
    private data class PageSource(val uri: Uri, val pdfPage: Int?)

    /** 고른 파일들을 페이지 단위로 펼친다. PDF 는 페이지 수만큼. */
    private fun expandPages(context: Context, files: List<Uri>): List<PageSource> = files.flatMap { uri ->
        if (isPdf(context, uri)) {
            context.contentResolver.openFileDescriptor(uri, "r").use { fd ->
                requireNotNull(fd) { "PDF 파일을 열 수 없습니다." }
                PdfRenderer(fd).use { renderer -> (0 until renderer.pageCount).map { PageSource(uri, it) } }
            }
        } else {
            listOf(PageSource(uri, null))
        }
    }

    /** 파일 크기(바이트). 알 수 없으면 0 */
    fun fileSize(context: Context, uri: Uri): Long =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L
            } ?: 0L
        }.getOrDefault(0L)

    fun fileName(context: Context, uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()

    fun isPdf(context: Context, uri: Uri): Boolean =
        context.contentResolver.getType(uri) == "application/pdf" ||
            uri.lastPathSegment?.endsWith(".pdf", ignoreCase = true) == true

    /**
     * 사진·PDF 를 페이지별 JPEG 로 압축해 저장하고 분석을 예약한다. 만들어진 계약서 id 를 돌려준다.
     * [folderId] 를 주면 그 폴더에 넣고, 없으면 분석 후 같은 부동산 폴더를 찾거나 새로 만든다.
     */
    suspend fun createContract(
        context: Context,
        files: List<Uri>,
        folderId: String?,
        onProgress: (done: Int, total: Int) -> Unit,
    ): String {
        require(files.isNotEmpty()) { "파일이 없습니다." }
        val pages = withContext(Dispatchers.IO) { expandPages(context, files) }
        require(pages.isNotEmpty()) { "페이지가 없는 파일입니다." }
        require(pages.size <= MAX_PAGES) {
            "계약서 한 건은 최대 ${MAX_PAGES}페이지까지입니다. (${pages.size}페이지)"
        }
        val uid = uid()
        val doc = contracts(uid).document()

        var thumbnail: ByteArray? = null
        val hashes = mutableListOf<String>()
        pages.forEachIndexed { index, page ->
            onProgress(index, pages.size)
            val (jpeg, thumb) = withContext(Dispatchers.IO) {
                val bitmap = if (page.pdfPage != null) renderPdfPage(context, page.uri, page.pdfPage) else decode(context, page.uri)
                val result = compressToFit(bitmap) to
                    if (index == 0) thumbnailOf(bitmap) else null
                bitmap.recycle()
                result
            }
            if (thumb != null) thumbnail = thumb
            hashes += sha(jpeg)
            pages(uid, doc.id).document("$index")
                .set(mapOf("index" to index, "image" to Blob.fromBytes(jpeg)))
                .await()
        }
        onProgress(pages.size, pages.size)

        // 같은 파일이 이미 올라가 있으면 (폴더 안에서 올릴 때는 그 폴더 안에서) 넣지 않는다.
        val duplicate = runCatching { allContracts() }.getOrDefault(emptyList()).firstOrNull { c ->
            c.pageHashes.isNotEmpty() && c.pageHashes.containsAll(hashes) && (folderId == null || c.folderId == folderId)
        }
        if (duplicate != null) {
            for (i in pages.indices) runCatching { pages(uid, doc.id).document("$i").delete().await() }
            throw DuplicateContractException("이미 올라간 계약서입니다 (${duplicate.title.ifBlank { "분석 중" }})")
        }

        doc.set(
            buildMap {
                put("status", "pending")
                put("pageCount", pages.size)
                put("thumbnail", thumbnail?.let(Blob::fromBytes))
                put("memo", "")
                put("title", "분석 중인 계약서")
                put("pageHashes", hashes)
                if (folderId != null) put("folderId", folderId)
                // 폴더 안에서 올렸으면 이미 확인된 것, 아니면 '새로 올린 계약서 정리'에서 확인
                put("confirmed", folderId != null)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            },
        ).await()

        AnalyzeWorker.enqueue(context, doc.id)
        return doc.id
    }

    private fun renderPdfPage(context: Context, uri: Uri, index: Int): Bitmap =
        context.contentResolver.openFileDescriptor(uri, "r").use { fd ->
            requireNotNull(fd) { "PDF 파일을 열 수 없습니다." }
            PdfRenderer(fd).use { renderer ->
                renderer.openPage(index).use { page ->
                    // PDF 한 페이지를 긴 변 2000px 정도로 그린다 (흰 바탕).
                    val scale = PDF_RENDER_EDGE.toFloat() / max(page.width, page.height)
                    val bitmap = Bitmap.createBitmap(
                        (page.width * scale).toInt().coerceAtLeast(1),
                        (page.height * scale).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }

    suspend fun reanalyze(context: Context, id: String) {
        setStatus(id, "pending")
        AnalyzeWorker.enqueue(context, id)
    }

    suspend fun setStatus(id: String, status: String, error: String? = null) {
        contracts(uid()).document(id).update(
            mapOf(
                "status" to status,
                "error" to (error ?: FieldValue.delete()),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    suspend fun saveAnalysis(id: String, analysis: Map<String, Any?>) {
        val parsed = Contract.parseAnalysis(analysis)
        contracts(uid()).document(id).update(
            mapOf(
                "status" to "done",
                "analysis" to analysis,
                "title" to parsed.propertyAddress.ifBlank { "주소 미확인 계약서" },
                "contractType" to parsed.contractType,
                // 화면에 보이지 않는 핵심 텍스트. 정리할 때 사진 대신 이것만 AI 에 보낸다.
                "digest" to SummaryText.digest(id, parsed),
                "sortDate" to parsed.representativeDate,
                "error" to FieldValue.delete(),
                "analyzedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    suspend fun setFolder(id: String, folderId: String) {
        contracts(uid()).document(id).update(
            mapOf("folderId" to folderId, "updatedAt" to FieldValue.serverTimestamp()),
        ).await()
    }

    /** 여러 계약서를 한 번에(모두 되거나 모두 안 되게) 다른 폴더로 옮긴다. 옮긴 계약서는 확인된 것으로 표시. */
    suspend fun setFolderAll(ids: Collection<String>, folderId: String) {
        val uid = uid()
        ids.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { id ->
                batch.update(
                    contracts(uid).document(id),
                    mapOf("folderId" to folderId, "confirmed" to true, "updatedAt" to FieldValue.serverTimestamp()),
                )
            }
            batch.commit().await()
        }
    }

    suspend fun setSuggestion(id: String, folderId: String?) {
        contracts(uid()).document(id).update("suggestedFolderId", folderId ?: FieldValue.delete()).await()
    }

    /** 새로 올린 계약서를 지금 폴더 그대로 확인 처리 */
    suspend fun confirmAll(ids: Collection<String>) {
        val uid = uid()
        ids.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { id -> batch.update(contracts(uid).document(id), "confirmed", true) }
            batch.commit().await()
        }
    }

    /**
     * 한 계약서가 파일 여러 개로 나뉘어 올라간 경우(앞뒷장 등) 하나로 합친다.
     * [ordered] 순서대로 페이지를 이어 붙여 첫 계약서에 모으고, 나머지 계약서는 지운 뒤 다시 분석한다.
     */
    suspend fun mergeContracts(context: Context, ordered: List<Contract>): String {
        require(ordered.size >= 2) { "합칠 계약서를 2개 이상 골라 주세요." }
        val total = ordered.sumOf { it.pageCount }
        require(total <= MAX_PAGES) { "합치면 ${total}페이지라서 최대 ${MAX_PAGES}페이지를 넘습니다." }
        val uid = uid()
        val target = ordered.first()
        var index = target.pageCount
        for (other in ordered.drop(1)) {
            for (i in 0 until other.pageCount) {
                val bytes = pageImage(other.id, i)
                pages(uid, target.id).document("$index")
                    .set(mapOf("index" to index, "image" to Blob.fromBytes(bytes)))
                    .await()
                index++
            }
        }
        val mergedHashes = ordered.flatMap { hashesOf(it) }
        contracts(uid).document(target.id).update(
            mapOf(
                "pageCount" to index,
                "pageHashes" to mergedHashes,
                "status" to "pending",
                "error" to FieldValue.delete(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        ordered.drop(1).forEach { delete(it) }
        AnalyzeWorker.enqueue(context, target.id)
        return target.id
    }

    private fun sha(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** 계약서의 페이지 지문. 예전 계약서라 저장된 것이 없으면 사진을 읽어 계산하고 저장해 둔다. */
    private suspend fun hashesOf(c: Contract): List<String> {
        if (c.pageHashes.size == c.pageCount && c.pageCount > 0) return c.pageHashes
        val hashes = (0 until c.pageCount).map { sha(pageImage(c.id, it)) }
        runCatching { contracts(uid()).document(c.id).update("pageHashes", hashes).await() }
        return hashes
    }

    /** [folderId] 폴더에 이 페이지들을 모두 담은 계약서가 이미 있는지 ([exceptId] 는 빼고) */
    private suspend fun folderHasPages(folderId: String, hashes: List<String>, exceptId: String?): Boolean =
        contractsInFolder(folderId).any { (c, _) -> c.id != exceptId && hashesOf(c).containsAll(hashes) }

    /** 계약서 전체를 [folderId] 로 옮기기 전에: 같은 계약서가 그 폴더에 이미 있으면 막는다. */
    suspend fun ensureNotDuplicateIn(folderId: String, contract: Contract) {
        if (contract.folderId == folderId) return
        if (folderHasPages(folderId, hashesOf(contract), contract.id)) {
            throw DuplicateContractException("이미 옮겨진 계약서입니다. 그 폴더에 같은 파일이 있어요.")
        }
    }

    private fun thumbnailOfJpeg(jpeg: ByteArray): ByteArray? =
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.let { b -> thumbnailOf(b).also { b.recycle() } }

    /**
     * 계약서의 일부 페이지를 떼어 [folderId] 폴더의 새 계약서로 옮긴다 (여러 서류가 한 계약서로 묶여 올라간 경우).
     * 옮긴 페이지는 원래 계약서에서 사라지고, 새 계약서와 남은 계약서 모두 다시 분석한다. 새 계약서 id 를 돌려준다.
     * 모든 페이지를 옮기는 경우는 호출하는 쪽에서 계약서 전체 옮기기로 처리한다.
     */
    suspend fun movePages(context: Context, contract: Contract, indices: List<Int>, folderId: String): String {
        val moving = indices.distinct().sorted().filter { it in 0 until contract.pageCount }
        require(moving.isNotEmpty()) { "옮길 페이지를 골라 주세요." }
        require(moving.size < contract.pageCount) { "모든 페이지를 옮길 때는 '전체 옮기기'를 쓰세요." }
        val uid = uid()
        val all = (0 until contract.pageCount).map { pageImage(contract.id, it) }
        val moved = moving.map { all[it] }
        val remaining = all.filterIndexed { i, _ -> i !in moving }
        val movedHashes = moved.map(::sha)
        if (folderHasPages(folderId, movedHashes, contract.id)) {
            throw DuplicateContractException("이미 옮겨진 계약서입니다. 그 폴더에 같은 파일이 있어요.")
        }

        // 1) 새 계약서 만들기
        val doc = contracts(uid).document()
        moved.forEachIndexed { i, bytes ->
            pages(uid, doc.id).document("$i").set(mapOf("index" to i, "image" to Blob.fromBytes(bytes))).await()
        }
        doc.set(
            mapOf(
                "status" to "pending",
                "pageCount" to moved.size,
                "thumbnail" to thumbnailOfJpeg(moved.first())?.let(Blob::fromBytes),
                "memo" to "",
                "title" to "분석 중인 계약서",
                "folderId" to folderId,
                "confirmed" to true,
                "pageHashes" to movedHashes,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()

        // 2) 원래 계약서에서 옮긴 페이지 빼기 (남은 페이지를 앞에서부터 다시 번호 매김)
        remaining.forEachIndexed { i, bytes ->
            pages(uid, contract.id).document("$i").set(mapOf("index" to i, "image" to Blob.fromBytes(bytes))).await()
        }
        for (i in remaining.size until contract.pageCount) pages(uid, contract.id).document("$i").delete().await()
        // 화면이 예전 사진을 다시 보여 주지 않도록, 계약서 정보를 바꾸기 전에 받아 둔 사진을 남은 페이지로 바꿔 둔다.
        pageCache.keys.removeAll { it.startsWith("${contract.id}/") }
        remaining.forEachIndexed { i, bytes -> pageCache["${contract.id}/$i"] = bytes }
        contracts(uid).document(contract.id).update(
            mapOf(
                "pageCount" to remaining.size,
                "pageHashes" to remaining.map(::sha),
                "thumbnail" to thumbnailOfJpeg(remaining.first())?.let(Blob::fromBytes),
                "status" to "pending",
                "error" to FieldValue.delete(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()

        AnalyzeWorker.enqueue(context, doc.id)
        AnalyzeWorker.enqueue(context, contract.id)
        return doc.id
    }

    suspend fun get(id: String): Contract? =
        contracts(uid()).document(id).get().await().takeIf { it.exists() }?.let(Contract::from)

    /** 정리용: 폴더 안 계약서와 숨겨 둔 핵심 텍스트 */
    suspend fun contractsInFolder(folderId: String): List<Pair<Contract, String>> =
        contracts(uid()).whereEqualTo("folderId", folderId).get().await().documents.map { doc ->
            val c = Contract.from(doc)
            val digest = doc.getString("digest")?.takeIf { it.isNotBlank() }
                ?: c.analysis?.let { SummaryText.digest(c.id, it) }.orEmpty()
            c to digest
        }

    suspend fun allContracts(): List<Contract> =
        contracts(uid()).get().await().documents.map(Contract::from)

    suspend fun updateMemo(id: String, memo: String) {
        contracts(uid()).document(id).update(
            mapOf("memo" to memo, "updatedAt" to FieldValue.serverTimestamp()),
        ).await()
    }

    suspend fun delete(contract: Contract) {
        val uid = uid()
        val batch = db.batch()
        pages(uid, contract.id).get().await().documents.forEach { batch.delete(it.reference) }
        batch.delete(contracts(uid).document(contract.id))
        batch.commit().await()
        pageCache.keys.removeAll { it.startsWith("${contract.id}/") }
    }

    private val pageCache = ConcurrentHashMap<String, ByteArray>()

    /** 페이지 사진 JPEG. 한 번 받은 사진은 메모리와 Firestore 오프라인 캐시에 남는다. */
    suspend fun pageImage(contractId: String, index: Int): ByteArray {
        val key = "$contractId/$index"
        pageCache[key]?.let { return it }
        val snap = pages(uid(), contractId).document("$index").get().await()
        val bytes = snap.getBlob("image")?.toBytes() ?: error("사진을 찾을 수 없습니다.")
        pageCache[key] = bytes
        return bytes
    }

    suspend fun contractPageCount(id: String): Int =
        contracts(uid()).document(id).get().await().getLong("pageCount")?.toInt() ?: 0

    suspend fun allPageImages(contractId: String, pageCount: Int): List<ByteArray> =
        (0 until pageCount).map { pageImage(contractId, it) }

    private fun decode(context: Context, uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    /** 글씨가 읽히는 선에서 크기·화질을 낮춰 가며 [MAX_PAGE_BYTES] 안으로 맞춘다. */
    private fun compressToFit(bitmap: Bitmap): ByteArray {
        for (edge in intArrayOf(2000, 1700, 1400, 1200)) {
            val scaled = scaleDown(bitmap, edge)
            try {
                for (quality in intArrayOf(85, 75, 65)) {
                    val bytes = scaled.toJpeg(quality)
                    if (bytes.size <= MAX_PAGE_BYTES) return bytes
                }
            } finally {
                if (scaled !== bitmap) scaled.recycle()
            }
        }
        return scaleDown(bitmap, 1000).toJpeg(60)
    }

    private fun thumbnailOf(bitmap: Bitmap): ByteArray = scaleDown(bitmap, THUMBNAIL_EDGE).toJpeg(70)

    private fun scaleDown(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val scale = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt(),
            (bitmap.height * scale).toInt(),
            true,
        )
    }

    private fun Bitmap.toJpeg(quality: Int): ByteArray = ByteArrayOutputStream().use { out ->
        compress(Bitmap.CompressFormat.JPEG, quality, out)
        out.toByteArray()
    }
}
