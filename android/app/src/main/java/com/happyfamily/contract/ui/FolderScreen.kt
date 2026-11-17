package com.happyfamily.contract.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.Close
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.happyfamily.contract.data.Contract
import com.happyfamily.contract.data.Folder
import com.happyfamily.contract.data.FolderStatus
import com.happyfamily.contract.data.Stakeholder
import com.happyfamily.contract.data.SummaryState
import com.happyfamily.contract.data.SummaryText
import com.happyfamily.contract.ui.theme.Brand

@Composable
fun FolderScreen(
    folderId: String,
    onBack: () -> Unit,
    onOpenContract: (String) -> Unit,
    importVm: ImportViewModel = viewModel(),
) {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val vm: FolderViewModel = viewModel(key = "folder-$folderId") { FolderViewModel(app, folderId) }
    val folder by vm.folder.collectAsStateWithLifecycle()
    val contracts by vm.contracts.collectAsStateWithLifecycle()
    val stakeholders by vm.stakeholders.collectAsStateWithLifecycle()
    val newestFirst by vm.newestFirst.collectAsStateWithLifecycle()
    val deleted by vm.deleted.collectAsStateWithLifecycle()
    val upload by importVm.upload.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val importMessage by importVm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }
    LaunchedEffect(importMessage) {
        importMessage?.let { snackbar.showSnackbar(it); importVm.consumeMessage() }
    }

    val importer = rememberImporter(importVm, folderId = folderId, onCreated = onOpenContract)

    val f = folder
    FolderContent(
        folder = if (deleted) null else f,
        contracts = contracts,
        stakeholders = stakeholders,
        newestFirst = newestFirst,
        upload = upload,
        snackbar = snackbar,
        onBack = onBack,
        onScan = importer.scan,
        onPickFiles = importer.pickFiles,
        onToggleSort = { vm.newestFirst.value = !newestFirst },
        onRename = vm::rename,
        onSetStatus = vm::setStatus,
        onSetStakeholder = vm::setStakeholder,
        onSaveSummary = vm::saveSummary,
        onApply = vm::apply,
        onCopy = { f?.let { copyToClipboard(context, it.name, SummaryText.folderDocument(it)) } },
        onExport = { f?.let { exportTextFile(context, "${it.name}_계약내용정리", SummaryText.folderDocument(it)) } },
        onDelete = { vm.delete(onBack) },
        onOpenContract = onOpenContract,
        onSplit = vm::split,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FolderContent(
    folder: Folder?,
    contracts: List<Contract>,
    stakeholders: List<Stakeholder>,
    newestFirst: Boolean,
    upload: UploadState?,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onPickFiles: () -> Unit,
    onToggleSort: () -> Unit,
    onRename: (String) -> Unit,
    onSetStatus: (FolderStatus) -> Unit,
    onSetStakeholder: (String) -> Unit,
    onSaveSummary: (String) -> Unit,
    onApply: () -> Unit,
    onCopy: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onOpenContract: (String) -> Unit,
    onSplit: (List<Contract>, String) -> Unit = { _, _ -> },
) {
    var renaming by remember { mutableStateOf(false) }
    /** null 이면 보통 화면, 아니면 분리하려고 고른 계약서 id 들 */
    var selecting by remember { mutableStateOf<Set<String>?>(null) }
    var splitting by remember { mutableStateOf(false) }
    var choosingStatus by remember { mutableStateOf(false) }
    var choosingStakeholder by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.background(Brand.ScreenGradient),
        topBar = {
            TopAppBar(
                title = {
                    Text(folder?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로") }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }, enabled = folder != null) {
                        Icon(Icons.Outlined.Delete, contentDescription = "폴더 삭제")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = Brand.Navy),
            )
        },
        bottomBar = {
            selecting?.let { sel ->
                val all = sel.size == contracts.size
                Column(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(16.dp)) {
                    Text(
                        if (all) "모든 계약서를 고르면 분리할 수 없어요. 폴더 이름을 바꾸려면 이름을 누르세요."
                        else "다른 부동산인 계약서를 고른 뒤 '분리하기'를 누르세요.",
                        color = Brand.TextSub,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WhitePillButton(text = "취소", icon = Icons.Outlined.Close, onClick = { selecting = null }, height = 52.dp)
                        GradientPillButton(
                            text = "분리하기 (${sel.size})",
                            onClick = { splitting = true },
                            enabled = sel.isNotEmpty() && !all,
                            showArrow = false,
                            height = 52.dp,
                            modifier = Modifier.weight(1f).alpha(if (sel.isNotEmpty() && !all) 1f else 0.5f),
                            leading = {
                                Icon(Icons.AutoMirrored.Outlined.CallSplit, contentDescription = null, tint = Color.White)
                            },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (folder == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val ongoingIds = folder.summaryEntries.filter { it.ongoing }.map { it.contractId }.toSet()
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            upload?.let { item { UploadProgress(it) } }
            item {
                FolderHeader(
                    folder = folder,
                    onRename = { renaming = true },
                    onStatus = { choosingStatus = true },
                    onStakeholder = { choosingStakeholder = true },
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    WhitePillButton(
                        text = "파일 업로드",
                        icon = Icons.Outlined.UploadFile,
                        onClick = onPickFiles,
                        enabled = upload == null,
                        height = 52.dp,
                        modifier = Modifier.weight(1f),
                    )
                    WhitePillButton(
                        text = "촬영 추가",
                        icon = Icons.Outlined.CameraAlt,
                        onClick = onScan,
                        enabled = upload == null,
                        height = 52.dp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                SummaryCard(
                    title = "계약내용정리",
                    text = folder.summaryText,
                    running = folder.summaryState == SummaryState.RUNNING,
                    message = folder.summaryError ?: folder.summaryNote,
                    edited = folder.summaryEdited,
                    emptyHint = if (contracts.isEmpty()) {
                        "아직 계약서가 없습니다. 위의 '파일 업로드'나 '촬영 추가'로 넣어 주세요."
                    } else {
                        "계약서 분석이 끝나면 자동으로 정리됩니다. 바로 정리하려면 '계약서 내용적용'을 누르세요."
                    },
                    onSave = onSaveSummary,
                    onApply = onApply,
                    onCopy = onCopy,
                    onExport = onExport,
                )
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("계약서", color = Brand.Navy, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Text("${contracts.size}", color = Brand.Blue, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (selecting == null && contracts.size >= 2) {
                        TextButton(onClick = { selecting = emptySet() }) { Text("골라서 분리") }
                    }
                    TextButton(onClick = onToggleSort) {
                        Icon(Icons.Outlined.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (newestFirst) "최근 계약부터" else "오래된 계약부터")
                    }
                }
            }
            items(contracts, key = { it.id }) { c ->
                val toggle = { selecting = selecting?.let { if (c.id in it) it - c.id else it + c.id } }
                ContractCard(
                    contract = c,
                    onClick = { if (selecting != null) toggle() else onOpenContract(c.id) },
                    ongoing = c.id in ongoingIds,
                    // 길게 누르면 분리할 계약서 고르기를 시작한다.
                    onLongClick = { if (selecting == null) selecting = setOf(c.id) else toggle() },
                    selected = selecting?.contains(c.id),
                )
            }
        }
    }

    if (folder != null && splitting) {
        val chosen = contracts.filter { it.id in selecting.orEmpty() }
        TextInputDialog(
            title = "새 폴더로 분리 (${chosen.size}건)",
            initial = chosen.firstNotNullOfOrNull { it.analysis?.folderName?.takeIf { n -> n.isNotBlank() } } ?: "",
            hint = "새 폴더 이름 (예: 목동 건영)",
            onDismiss = { splitting = false },
            onConfirm = { name ->
                splitting = false
                selecting = null
                onSplit(chosen, name)
            },
        )
    }
    if (folder != null && renaming) {
        TextInputDialog(
            title = "폴더 이름",
            initial = folder.name,
            hint = "예: 과천 미소가, 목동 건영",
            onDismiss = { renaming = false },
            onConfirm = { onRename(it); renaming = false },
        )
    }
    if (folder != null && choosingStatus) {
        AlertDialog(
            onDismissRequest = { choosingStatus = false },
            title = { Text("폴더 상태") },
            text = {
                Column {
                    Text(
                        "보유 중이거나 임차·임대 중이면 진행중, 매도를 마쳤거나 계약이 끝났으면 완료입니다. 계약서 내용적용을 누르면 다시 자동으로 정해집니다.",
                        color = Brand.TextSub,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    FolderStatus.entries.forEach { s ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSetStatus(s); choosingStatus = false }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = folder.status == s, onClick = { onSetStatus(s); choosingStatus = false })
                            Text(s.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingStatus = false }) { Text("닫기") } },
        )
    }
    if (folder != null && choosingStakeholder) {
        StakeholderPickerDialog(
            current = folder.stakeholder,
            stakeholders = stakeholders,
            onDismiss = { choosingStakeholder = false },
            onPick = { onSetStakeholder(it); choosingStakeholder = false },
        )
    }
    if (folder != null && confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("폴더 삭제") },
            text = { Text("'${folder.name}' 폴더와 안의 계약서 ${contracts.size}건, 사진을 모두 삭제할까요?\n되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun FolderHeader(folder: Folder, onRename: () -> Unit, onStatus: () -> Unit, onStakeholder: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(folder.thumbnail, size = 72.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.clickable(onClick = onRename), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        folder.name,
                        color = Brand.Navy,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.Edit, contentDescription = "이름 바꾸기", tint = Brand.TextSub, modifier = Modifier.size(18.dp))
                }
                if (folder.address.isNotBlank()) {
                    Text(folder.address, color = Brand.TextSub, fontSize = 13.sp, lineHeight = 18.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onStatus)) { StatusBadge(folder.status) }
                    Spacer(Modifier.width(8.dp))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Brand.CardTint)
                            .clickable(onClick = onStakeholder)
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Person, contentDescription = null, tint = Brand.Blue, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            folder.stakeholder.ifBlank { "이해관계자 입력" },
                            color = Brand.Blue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    hint: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(30) },
                singleLine = true,
                placeholder = { Text(hint) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** 이 폴더를 누구 기준으로 볼지 고른다. 새 이름을 입력하면 이해관계자로 추가된다. */
@Composable
fun StakeholderPickerDialog(
    current: String,
    stakeholders: List<Stakeholder>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이해관계자") },
        text = {
            Column {
                Text(
                    "이 폴더의 계약을 누구 입장에서 정리할지 고르세요. 그 사람 기준으로 매수·매도, 진행중·완료가 정해집니다.",
                    color = Brand.TextSub,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(8.dp))
                stakeholders.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(s.name) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = current == s.name, onClick = { onPick(s.name) })
                        Text(s.name + if (s.isDefault) " (기본)" else "")
                    }
                }
                if (stakeholders.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(30) },
                    singleLine = true,
                    label = { Text("새 이름 입력") },
                    placeholder = { Text("예: 홍길동") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(newName.trim()) }, enabled = newName.isNotBlank()) { Text("추가하고 선택") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}
