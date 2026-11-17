package com.happyfamily.contract.ui

import android.app.Activity
import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.happyfamily.contract.data.ContractRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UploadState(val done: Int, val total: Int)

/** 계약서 사진·PDF 를 저장하는 일. 화면을 돌려도 이어지도록 ViewModel 에 둔다. */
class ImportViewModel(application: Application) : AndroidViewModel(application) {
    private val _upload = MutableStateFlow<UploadState?>(null)
    val upload: StateFlow<UploadState?> = _upload.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun upload(files: List<Uri>, folderId: String?, onCreated: (String) -> Unit) {
        if (files.isEmpty() || _upload.value != null) return
        _upload.value = UploadState(0, files.size)
        viewModelScope.launch {
            try {
                val id = ContractRepository.createContract(getApplication(), files, folderId) { done, total ->
                    _upload.value = UploadState(done, total)
                }
                onCreated(id)
            } catch (e: Exception) {
                _message.value = "저장 실패: ${e.message}"
            } finally {
                _upload.value = null
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
 *  - scan: 구글 문서 스캐너로 촬영 (테두리 자동 보정, 갤러리 가져오기 포함)
 *  - pickFiles: 파일에서 PDF·JPG 등 고르기
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
            vm.upload(pages, folderId, onCreated)
        }
    }
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> vm.upload(uris, folderId, onCreated) }

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
