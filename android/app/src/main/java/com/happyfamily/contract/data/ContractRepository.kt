package com.happyfamily.contract.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
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
 *  - users/{uid}/contracts/{contractId}            계약서 정보, 분석 결과, 썸네일
 *  - users/{uid}/contracts/{contractId}/pages/{n}  페이지 사진 (JPEG, 문서 1개 1MB 한도 안으로 압축)
 *
 * 사진을 저장하고 status = "pending" 으로 만든 뒤 [AnalyzeWorker] 가 Gemini 로 분석한다.
 */
object ContractRepository {
    const val MAX_PAGES = 15

    // Firestore 문서 1개는 1MiB 까지라 그보다 충분히 작게 줄인다.
    private const val MAX_PAGE_BYTES = 700_000
    private const val THUMBNAIL_EDGE = 240

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

    /** 사진을 압축해 저장하고 분석을 예약한다. 만들어진 계약서 id 를 돌려준다. */
    suspend fun createContract(
        context: Context,
        pages: List<Uri>,
        onProgress: (done: Int, total: Int) -> Unit,
    ): String {
        require(pages.isNotEmpty()) { "사진이 없습니다." }
        require(pages.size <= MAX_PAGES) { "사진은 최대 ${MAX_PAGES}장까지 올릴 수 있습니다." }
        val uid = uid()
        val doc = contracts(uid).document()

        var thumbnail: ByteArray? = null
        pages.forEachIndexed { index, uri ->
            onProgress(index, pages.size)
            val (jpeg, thumb) = withContext(Dispatchers.IO) {
                val bitmap = decode(context, uri)
                val result = compressToFit(bitmap) to
                    if (index == 0) thumbnailOf(bitmap) else null
                bitmap.recycle()
                result
            }
            if (thumb != null) thumbnail = thumb
            pages(uid, doc.id).document("$index")
                .set(mapOf("index" to index, "image" to Blob.fromBytes(jpeg)))
                .await()
        }
        onProgress(pages.size, pages.size)

        doc.set(
            mapOf(
                "status" to "pending",
                "pageCount" to pages.size,
                "thumbnail" to thumbnail?.let(Blob::fromBytes),
                "memo" to "",
                "title" to "분석 중인 계약서",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()

        AnalyzeWorker.enqueue(context, doc.id)
        return doc.id
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
        val address = analysis["propertyAddress"] as? String
        contracts(uid()).document(id).update(
            mapOf(
                "status" to "done",
                "analysis" to analysis,
                "title" to (address?.takeIf { it.isNotBlank() } ?: "주소 미확인 계약서"),
                "contractType" to (analysis["contractType"] as? String).orEmpty(),
                "error" to FieldValue.delete(),
                "analyzedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

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
