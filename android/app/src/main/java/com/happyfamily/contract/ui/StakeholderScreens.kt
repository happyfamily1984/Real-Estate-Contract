package com.happyfamily.contract.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.happyfamily.contract.data.FolderRepository
import com.happyfamily.contract.data.Stakeholder
import com.happyfamily.contract.ui.theme.Brand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 이해관계자 목록: 추가, 기본(본인) 지정, 삭제. 누르면 그 사람 기준 정리 문서로 간다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StakeholdersScreen(onBack: () -> Unit, onOpenReport: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val flow = remember { FolderRepository.stakeholdersFlow() }
    val stakeholders by flow.collectAsStateWithLifecycle(initialValue = null)
    var newName by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<Stakeholder?>(null) }
    var toRename by remember { mutableStateOf<Stakeholder?>(null) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    fun launch(block: suspend () -> Unit) = scope.launch {
        runCatching { block() }.onFailure { snackbar.showSnackbar(it.message ?: it.toString()) }
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.background(Brand.ScreenGradient),
        topBar = {
            TopAppBar(
                title = { Text("이해관계자", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = Brand.Navy),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "본인·가족 등 이름을 입력하세요. 계약서의 임대인·임차인(매도인·매수인)에 이 이름이 있으면 그 폴더의 이해관계자로 자동 표시되고, 그 사람 기준으로 매수·매도, 진행중·완료가 정리됩니다. 계약서에 없는 이름은 표시되지 않습니다.",
                    color = Brand.TextSub,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it.take(30) },
                        singleLine = true,
                        placeholder = { Text("이름 입력 (예: 홍길동)") },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = {
                            val name = newName.trim()
                            newName = ""
                            launch {
                                FolderRepository.ensureStakeholder(name)
                                val n = FolderRepository.reassignAllStakeholders(context)
                                snackbar.showSnackbar(if (n > 0) "'$name' 을(를) 추가하고 폴더 ${n}개에 반영했어요." else "'$name' 을(를) 추가했어요. 이 이름이 나오는 계약서는 아직 없어요.")
                            }
                        },
                        enabled = newName.isNotBlank(),
                    ) { Text("추가") }
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        launch {
                            val n = FolderRepository.reassignAllStakeholders(context, includeManual = true)
                            snackbar.showSnackbar("모든 폴더의 이해관계자를 계약서 당사자 기준으로 다시 맞췄어요. (바뀐 폴더 ${n}개)")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("모든 폴더에 이해관계자 다시 적용") }
            }
            val list = stakeholders
            if (list == null) {
                item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else {
                items(list, key = { it.id }) { s ->
                    Card(
                        onClick = { onOpenReport(s.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Row(Modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.name, color = Brand.Navy, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("계약내용정리 문서 보기", color = Brand.TextSub, fontSize = 13.sp)
                            }
                            IconButton(onClick = { toRename = s }) {
                                Icon(Icons.Outlined.Edit, contentDescription = "이름 수정", tint = Brand.TextSub)
                            }
                            IconButton(onClick = { toDelete = s }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "삭제", tint = Brand.TextSub)
                            }
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Brand.TextSub)
                        }
                    }
                }
            }
        }
    }

    toRename?.let { s ->
        TextInputDialog(
            title = "이해관계자 이름 수정",
            initial = s.name,
            hint = "예: 홍길동",
            onDismiss = { toRename = null },
            onConfirm = { name ->
                toRename = null
                launch {
                    FolderRepository.renameStakeholder(context, s.id, name)
                    snackbar.showSnackbar("이름을 '$name' (으)로 바꾸고 폴더에 다시 적용했어요.")
                }
            },
        )
    }

    toDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("이해관계자 삭제") },
            text = { Text("'${s.name}' 을(를) 목록에서 지울까요? 폴더의 이해관계자 표시에서도 빠집니다. 계약서와 폴더는 지워지지 않습니다.") },
            confirmButton = {
                TextButton(onClick = { toDelete = null; launch { FolderRepository.deleteStakeholder(context, s.id) } }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("취소") } },
        )
    }
}

class StakeholderReportViewModel(application: Application, stakeholderId: String) : AndroidViewModel(application) {
    val stakeholder: StateFlow<Stakeholder?> = FolderRepository.stakeholderFlow(stakeholderId)
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _note = MutableStateFlow<String?>(null)
    val note: StateFlow<String?> = _note.asStateFlow()

    private var autoApplied = false

    /** 처음 열었는데 정리 문서가 비어 있으면 한 번 자동으로 만든다. */
    fun applyIfEmpty(s: Stakeholder) {
        if (autoApplied || s.report.isNotBlank()) return
        autoApplied = true
        apply(s)
    }

    /** 적용: 이 사람과 관련된 모든 폴더를 저장해 둔 핵심 텍스트로 다시 정리 */
    fun apply(s: Stakeholder) {
        if (_running.value) return
        _running.value = true
        _note.value = null
        viewModelScope.launch {
            try {
                _note.value = FolderRepository.applyStakeholderReport(getApplication(), s).second
            } catch (e: Exception) {
                _note.value = "정리하지 못했습니다: ${e.message}"
            } finally {
                _running.value = false
            }
        }
    }

    fun save(s: Stakeholder, text: String) {
        viewModelScope.launch {
            runCatching { FolderRepository.saveReport(s.id, text, edited = true) }
                .onFailure { _note.value = "저장하지 못했습니다: ${it.message}" }
        }
    }
}

/** 이해관계자 기준으로 과거부터 모든 계약을 정리한 문서 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StakeholderReportScreen(stakeholderId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: StakeholderReportViewModel = viewModel(key = "report-$stakeholderId") {
        StakeholderReportViewModel(app, stakeholderId)
    }
    val stakeholder by vm.stakeholder.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val note by vm.note.collectAsStateWithLifecycle()

    LaunchedEffect(stakeholder?.id) { stakeholder?.let(vm::applyIfEmpty) }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.background(Brand.ScreenGradient),
        topBar = {
            TopAppBar(
                title = { Text("${stakeholder?.name.orEmpty()} 계약내용정리", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = Brand.Navy),
            )
        },
    ) { padding ->
        val s = stakeholder
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 40.dp),
        ) {
            item {
                SummaryCard(
                    title = "${s.name} 기준 전체 계약",
                    text = s.report,
                    running = running,
                    message = note,
                    edited = s.reportEdited,
                    emptyHint = "'계약서 내용적용'을 누르면 ${s.name} 님과 관련된 모든 계약을 과거부터 정리합니다.",
                    onSave = { vm.save(s, it) },
                    onApply = { vm.apply(s) },
                    onCopy = { copyToClipboard(context, s.name, s.report) },
                    onExport = { exportTextFile(context, "${s.name}_계약내용정리", s.report) },
                )
            }
        }
    }
}
