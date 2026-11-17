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

    val allFolders: StateFlow<List<Folder>> = FolderRepository.foldersFlow()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 고른 계약서들을 다른 폴더(또는 새 폴더)로 옮기기 */
    fun moveContracts(contracts: List<Contract>, target: String?, newName: String) = run {
        val (_, name) = FolderRepository.moveContracts(getApplication(), contracts, target, newName)
        _message.value = "계약서 ${contracts.size}건을 '$name' 폴더로 옮겼어요."
    }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _message.value = friendlyError(e)
            }
        }
    }

    fun rename(name: String) = run { if (name.isNotBlank()) FolderRepository.rename(folderId, name) }

    /** 진행중/완료 직접 정하기. null 이면 자동(계약 내용으로 판단) */
    fun setStatus(status: FolderStatus?) = run { FolderRepository.setStatus(getApplication(), folderId, status) }

    /** 이해관계자 직접 정하기 (여러 명 가능, 비우면 없음) */
    fun setStakeholders(names: List<String>) = run {
        FolderRepository.setStakeholders(folderId, names)
        if (folder.value?.summaryEdited == true) {
            _message.value = "직접 고친 정리는 그대로 두었어요. '계약서 내용적용'을 누르면 바뀐 이해관계자 기준으로 다시 정리합니다."
        } else {
            SummaryWorker.enqueue(getApplication(), folderId, force = false)
        }
    }

    /** 앞뒷장 등으로 나뉜 계약서들을 고른 순서대로 한 계약서로 합치고 다시 분석 */
    fun mergeContracts(ordered: List<Contract>) = run {
        ContractRepository.mergeContracts(getApplication(), ordered)
        _message.value = "계약서 ${ordered.size}건을 한 계약서로 합쳤어요. 합친 페이지로 다시 분석합니다."
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
