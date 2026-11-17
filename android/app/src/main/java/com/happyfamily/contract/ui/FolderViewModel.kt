package com.happyfamily.contract.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.happyfamily.contract.data.Contract
import com.happyfamily.contract.data.ContractRepository
import com.happyfamily.contract.data.Folder
import com.happyfamily.contract.data.FolderRepository
import com.happyfamily.contract.data.FolderStatus
import com.happyfamily.contract.data.Stakeholder
import com.happyfamily.contract.data.SummaryWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FolderViewModel(application: Application, val folderId: String) : AndroidViewModel(application) {

    /** false: 오래된 계약부터(과거 → 현재), true: 최근 계약부터 */
    val newestFirst = MutableStateFlow(false)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 처음엔 null(불러오는 중). 폴더가 지워졌으면 [deleted] 가 true. */
    val folder: StateFlow<Folder?> = FolderRepository.folderFlow(folderId)
        .catch { _message.value = it.message }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    val contracts: StateFlow<List<Contract>> = combine(
        ContractRepository.contractsFlow().map { list -> list.filter { it.folderId == folderId } }
            .catch { emit(emptyList()) },
        newestFirst,
    ) { list, newest ->
        val sorted = list.sortedBy { c -> c.sortDate.ifBlank { c.analysis?.representativeDate.orEmpty() }.ifBlank { "9999" } }
        if (newest) sorted.reversed() else sorted
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stakeholders: StateFlow<List<Stakeholder>> = FolderRepository.stakeholdersFlow()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _message.value = e.message ?: e.toString()
            }
        }
    }

    fun rename(name: String) = run { if (name.isNotBlank()) FolderRepository.rename(folderId, name) }

    fun setStatus(status: FolderStatus) = run { FolderRepository.setStatus(folderId, status) }

    fun setStakeholder(name: String) = run {
        FolderRepository.setStakeholder(folderId, name)
        if (folder.value?.summaryEdited == true) {
            _message.value = "직접 고친 정리는 그대로 두었어요. '계약서 내용적용'을 누르면 ${name.ifBlank { "새" }} 기준으로 다시 정리합니다."
        } else {
            SummaryWorker.enqueue(getApplication(), folderId, force = false)
        }
    }

    fun saveSummary(text: String) = run { FolderRepository.saveSummaryText(folderId, text) }

    /** 계약서 내용적용: 저장해 둔 핵심 텍스트로 다시 정리 (직접 고친 내용은 덮어씀) */
    fun apply() = SummaryWorker.enqueue(getApplication(), folderId, force = true)

    /** 고른 계약서들을 새 폴더로 분리 */
    fun split(contracts: List<Contract>, name: String) = run {
        FolderRepository.splitContracts(getApplication(), folderId, contracts, name)
        _message.value = "계약서 ${contracts.size}건을 새 폴더 '${name.ifBlank { "새 계약 폴더" }}'(으)로 분리했어요. 홈 화면에서 볼 수 있어요."
    }

    fun delete(onDone: () -> Unit) = run {
        _deleted.value = true
        FolderRepository.deleteFolder(folderId)
        onDone()
    }

    fun consumeMessage() {
        _message.value = null
    }
}
