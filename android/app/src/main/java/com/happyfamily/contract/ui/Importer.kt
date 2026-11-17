package com.happyfamily.contract.ui

import android.app.Activity
import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.happyfamily.contract.data.ContractRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 저장 진행 상황. [done]/[total] 은 계약서(파일 묶음) 단위. */
data class UploadState(val done: Int, val total: Int, val detail: String = "")

/** 사진이 여러 장일 때 어떻게 묶을지 물어보는 중인 업로드 */
data class PendingImport(val pdfs: List<Uri>, val images: List<Uri>, val folderId: String?)

/** 계약서 사진·PDF 를 저장하는 일. 화면을 돌려도 이어지도록 ViewModel 에 둔다. */
class ImportViewModel(application: Application) : AndroidViewModel(application) {
    private val _upload = MutableStateFlow<UploadState?>(null)
    val upload: StateFlow<UploadState?> = _upload.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _pending = MutableStateFlow<PendingImport?>(null)
    val pending: StateFlow<PendingImport?> = _pending.asStateFlow()

    /** 스캐너로 찍은 페이지들 → 계약서 한 건 */
    fun uploadScan(pages: List<Uri>, folderId: String?, onCreated: (String) -> Unit) {
        if (pages.isEmpty()) return
        start(listOf(pages), folderId, onCreated)
    }

    /**
     * 파일에서 고른 PDF·사진들. PDF 는 파일마다 계약서 한 건이 된다.
     * 사진이 여러 장이면 한 계약서로 묶을지, 사진마다 따로 올릴지 물어본다.
     */
    /** 여러 건을 올렸을 때 (폴더 밖에서) — 정리 화면으로 보낸다 */
    var onCreatedMany: (() -> Unit)? = null

    fun uploadFiles(files: List<Uri>, folderId: String?, onCreated: (String) -> Unit) {
        if (files.isEmpty() || _upload.value != null) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            if (files.size > ContractRepository.MAX_FILES) {
                _message.value = "한 번에 최대 ${ContractRepository.MAX_FILES}개까지 올릴 수 있습니다. (${files.size}개 고름)"
                return@launch
            }
            val (pdfs, images, total) = withContext(Dispatchers.IO) {
                val pdfs = files.filter { ContractRepository.isPdf(app, it) }
                Triple(pdfs, files - pdfs.toSet(), files.sumOf { ContractRepository.fileSize(app, it) })
            }
            if (total > ContractRepository.MAX_TOTAL_BYTES) {
                _message.value = "한 번에 100MB까지 올릴 수 있습니다. (고른 파일 ${"%.1f".format(total / 1024.0 / 1024.0)}MB)"
                return@launch
            }
            if (images.size > 1) {
                _pending.value = PendingImport(pdfs, images, folderId)
            } else {
                start(pdfs.map { listOf(it) } + images.map { listOf(it) }, folderId, onCreated)
            }
        }
    }

    /** 사진 묶기 선택에 대한 답 */
    fun resolvePending(groupImages: Boolean, onCreated: (String) -> Unit) {
        val p = _pending.value ?: return
        _pending.value = null
        val imageGroups = if (groupImages) {
            // 한 계약서는 최대 MAX_PAGES 페이지라서 그보다 많으면 나눈다.
            p.images.chunked(ContractRepository.MAX_PAGES)
        } else {
            p.images.map { listOf(it) }
        }
        start(p.pdfs.map { listOf(it) } + imageGroups, p.folderId, onCreated)
    }

    fun cancelPending() {
        _pending.value = null
    }

    /** 묶음 하나가 계약서 한 건. 차례로 저장하고 분석 대기열에 넣는다. */
    private fun start(groups: List<List<Uri>>, folderId: String?, onCreated: (String) -> Unit) {
        if (groups.isEmpty() || _upload.value != null) return
        val app = getApplication<Application>()
        _upload.value = UploadState(0, groups.size)
        viewModelScope.launch {
            val created = mutableListOf<String>()
            val failed = mutableListOf<String>()
            groups.forEachIndexed { index, group ->
                val name = if (group.size == 1) ContractRepository.fileName(app, group[0]) else "사진 ${group.size}장"
                try {
                    created += ContractRepository.createContract(app, group, folderId) { pageDone, pageTotal ->
                        _upload.value = UploadState(index, groups.size, "$name · $pageDone/${pageTotal}페이지")
                    }
                } catch (e: Exception) {
                    failed += "$name: ${e.message}"
                }
            }
            _upload.value = null
            when {
                created.size == 1 && failed.isEmpty() -> onCreated(created[0])
                created.size > 1 && folderId == null && onCreatedMany != null -> {
                    onCreatedMany?.invoke()
                    if (failed.isNotEmpty()) _message.value = "실패 ${failed.size}건: " + failed.take(3).joinToString(" / ")
                }
                created.isNotEmpty() -> _message.value = buildString {
                    append("계약서 ${created.size}건을 저장했어요. 순서대로 분석한 뒤 폴더로 정리됩니다.")
                    if (failed.isNotEmpty()) append(" 실패 ${failed.size}건: ").append(failed.take(3).joinToString(" / "))
                }
                failed.isNotEmpty() -> _message.value = "저장 실패: " + failed.take(3).joinToString(" / ")
            }
        }
    }

    fun showMessage(text: String) {
        _message.value = text
    }

    fun consumeMessage() {
        _message.value = null
    }
}

class Importer(val scan: () -> Unit, val pickFiles: () -> Unit)

/**
 * 계약서를 들여오는 두 가지 방법.
 *  - scan: 구글 문서 스캐너로 촬영 (테두리 자동 보정, 갤러리 가져오기 포함) → 계약서 한 건
 *  - pickFiles: 휴대폰의 PDF·JPG 파일 고르기 (최대 100개, 100MB)
 */
@Composable
fun rememberImporter(vm: ImportViewModel, folderId: String?, onCreated: (String) -> Unit): Importer {
    val activity = LocalContext.current as Activity

    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pages = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                ?.pages?.map { it.imageUri }.orEmpty()
            vm.uploadScan(pages, folderId, onCreated)
        }
    }
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> vm.uploadFiles(uris, folderId, onCreated) }

    // 사진 여러 장을 골랐을 때: 한 계약서로 묶을지 물어본다.
    val pending by vm.pending.collectAsStateWithLifecycle()
    pending?.let { p ->
        AlertDialog(
            onDismissRequest = vm::cancelPending,
            title = { Text("사진 ${p.images.size}장을 어떻게 올릴까요?") },
            text = {
                Column {
                    Text("한 계약서의 여러 페이지라면 '한 계약서로 묶기', 서로 다른 서류라면 '사진마다 따로'를 고르세요.")
                    if (p.pdfs.isNotEmpty()) Text("\nPDF ${p.pdfs.size}개는 파일마다 따로 올라갑니다.")
                }
            },
            confirmButton = { TextButton(onClick = { vm.resolvePending(true, onCreated) }) { Text("한 계약서로 묶기") } },
            dismissButton = { TextButton(onClick = { vm.resolvePending(false, onCreated) }) { Text("사진마다 따로") } },
        )
    }

    return remember(folderId) {
        val pickFiles = { fileLauncher.launch(arrayOf("application/pdf", "image/*")) }
        Importer(
            scan = {
                val options = GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(true)
                    .setPageLimit(ContractRepository.MAX_PAGES)
                    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .build()
                GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                    .addOnSuccessListener { sender ->
                        scannerLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    }
                    .addOnFailureListener {
                        vm.showMessage("스캐너를 열 수 없어 파일 선택을 엽니다.")
                        pickFiles()
                    }
            },
            pickFiles = pickFiles,
        )
    }
}
