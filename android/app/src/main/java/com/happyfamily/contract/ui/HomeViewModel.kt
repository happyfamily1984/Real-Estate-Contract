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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 홈 화면에 보여 줄 내용 */
data class HomeState(
    /** 아직 폴더가 정해지지 않은(분석 중·실패) 계약서 */
    val unfiled: List<Contract>,
    val ongoing: List<Folder>,
    val done: List<Folder>,
    val contractCounts: Map<String, Int>,
    val stakeholders: List<Stakeholder>,
    val isEmpty: Boolean,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    val query = MutableStateFlow("")

    /** null 이면 전체, 아니면 그 이해관계자의 폴더만 */
    val stakeholderFilter = MutableStateFlow<String?>(null)

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    private val filters = combine(query, stakeholderFilter) { q, s -> q to s }

    /** null 이면 아직 불러오는 중 */
    val state: StateFlow<HomeState?> = combine(
        FolderRepository.foldersFlow().catch { _loadError.value = it.message },
        ContractRepository.contractsFlow().catch { _loadError.value = it.message },
        FolderRepository.stakeholdersFlow().catch { emit(emptyList()) },
        filters,
    ) { folders, contracts, stakeholders, (q, who) ->
        val counts = contracts.mapNotNull { it.folderId }.groupingBy { it }.eachCount()
        val shown = folders
            .filter { who == null || it.stakeholder == who }
            .filter { f -> f.matches(q) || contracts.any { it.folderId == f.id && it.matches(q) } }
            .sortedBy { it.name }
        HomeState(
            unfiled = contracts.filter { it.folderId == null && it.matches(q) },
            ongoing = shown.filter { it.status == FolderStatus.ONGOING },
            done = shown.filter { it.status == FolderStatus.DONE },
            contractCounts = counts,
            stakeholders = stakeholders,
            isEmpty = folders.isEmpty() && contracts.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // 폴더 기능 이전에 저장된 계약서를 폴더로 옮긴다.
        viewModelScope.launch { runCatching { FolderRepository.fileUnfiledContracts(getApplication()) } }
    }
}
