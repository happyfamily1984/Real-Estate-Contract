package com.happyfamily.contract.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.happyfamily.contract.data.Contract
import com.happyfamily.contract.data.ContractRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UploadState(val done: Int, val total: Int)

class ContractListViewModel(application: Application) : AndroidViewModel(application) {

    val query = MutableStateFlow("")

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    /** null 이면 아직 불러오는 중. */
    val contracts: StateFlow<List<Contract>?> =
        combine(
            ContractRepository.contractsFlow().catch { _loadError.value = it.message },
            query,
        ) { list, q -> list.filter { it.matches(q) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _upload = MutableStateFlow<UploadState?>(null)
    val upload: StateFlow<UploadState?> = _upload.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun upload(pages: List<Uri>, onCreated: (String) -> Unit) {
        if (pages.isEmpty() || _upload.value != null) return
        _upload.value = UploadState(0, pages.size)
        viewModelScope.launch {
            try {
                val id = ContractRepository.createContract(getApplication(), pages) { done, total ->
                    _upload.value = UploadState(done, total)
                }
                onCreated(id)
            } catch (e: Exception) {
                _message.value = "업로드 실패: ${e.message}"
            } finally {
                _upload.value = null
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
