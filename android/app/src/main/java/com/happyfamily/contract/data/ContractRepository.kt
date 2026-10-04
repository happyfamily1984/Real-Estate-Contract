package com.happyfamily.contract.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import com.google.firebase.storage.storageMetadata
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
 * 저장 구조
 *  - Firestore: users/{uid}/contracts/{contractId}
 *  - Storage:   users/{uid}/contracts/{contractId}/page_1.jpg ...
 * 사진을 올리고 문서를 status = "pending" 으로 만들면 서버(Cloud Functions)가 분석해서
 * analysis 필드를 채운다. 앱은 실시간 리스너로 결과를 받는다.
 */
object ContractRepository {
    private const val MAX_EDGE = 2400
    private const val JPEG_QUALITY = 88

    private val db get() = Firebase.firestore
    private val storage get() = Firebase.storage

    private fun uid(): String = AuthRepository.currentUser?.uid ?: error("로그인이 필요합니다.")

    private fun contracts(uid: String) = db.collection("users").document(uid).collection("contracts")

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

    /** 사진을 JPEG 로 줄여 올리고 분석 요청 문서를 만든다. 만들어진 계약서 id 를 돌려준다. */
    suspend fun createContract(
        context: Context,
        pages: List<Uri>,
        onProgress: (done: Int, total: Int) -> Unit,
    ): String {
        require(pages.isNotEmpty()) { "사진이 없습니다." }
        require(pages.size <= 20) { "사진은 최대 20장까지 올릴 수 있습니다." }
        val uid = uid()
        val doc = contracts(uid).document()
        val metadata = storageMetadata { contentType = "image/jpeg" }

        val paths = pages.mapIndexed { index, uri ->
            onProgress(index, pages.size)
            val path = "users/$uid/contracts/${doc.id}/page_${index + 1}.jpg"
            val bytes = withContext(Dispatchers.IO) { compressToJpeg(context, uri) }
            storage.reference.child(path).putBytes(bytes, metadata).await()
            path
        }
        onProgress(pages.size, pages.size)

        doc.set(
            mapOf(
                "status" to "pending",
                "imagePaths" to paths,
                "memo" to "",
                "title" to "분석 중인 계약서",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        return doc.id
    }

    suspend fun reanalyze(id: String) {
        contracts(uid()).document(id).update(
            mapOf("status" to "pending", "updatedAt" to FieldValue.serverTimestamp()),
        ).await()
    }

    suspend fun updateMemo(id: String, memo: String) {
        contracts(uid()).document(id).update(
            mapOf("memo" to memo, "updatedAt" to FieldValue.serverTimestamp()),
        ).await()
    }

    suspend fun delete(contract: Contract) {
        contract.imagePaths.forEach { path ->
            runCatching { storage.reference.child(path).delete().await() }
        }
        contracts(uid()).document(contract.id).delete().await()
    }

    private val downloadUrlCache = ConcurrentHashMap<String, String>()

    /** 사진 표시용 URL. 본인 계정으로만 받을 수 있다(Storage 보안 규칙). */
    suspend fun imageUrl(path: String): String =
        downloadUrlCache[path] ?: storage.reference.child(path).downloadUrl.await().toString()
            .also { downloadUrlCache[path] = it }

    private fun compressToJpeg(context: Context, uri: Uri): ByteArray {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_EDGE) {
                val scale = MAX_EDGE.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).toInt(),
                    (info.size.height * scale).toInt(),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }
}
