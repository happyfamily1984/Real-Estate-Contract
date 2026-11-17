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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material3.Checkbox
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
    val allFolders by vm.allFolders.collectAsStateWithLifecycle()
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
        onSetStakeholders = vm::setStakeholders,
        onSaveSummary = vm::saveSummary,
        onApply = vm::apply,
        onCopy = { f?.let { copyToClipboard(context, it.name, SummaryText.folderDocument(it)) } },
        onExport = { f?.let { exportTextFile(context, "${it.name}_계약내용정리", SummaryText.folderDocument(it)) } },
        onDelete = { vm.delete(onBack) },
        onOpenContract = onOpenContract,
        onSplit = vm::split,
        onMergeContracts = vm::mergeContracts,
        allFolders = allFolders,
        onMoveContracts = vm::moveContracts,
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
    onSetStatus: (FolderStatus?) -> Unit,
    onSetStakeholders: (List<String>) -> Unit,
    onSaveSummary: (String) -> Unit,
    onApply: () -> Unit,
    onCopy: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onOpenContract: (String) -> Unit,
    onSplit: (List<Contract>, String) -> Unit = { _, _ -> },
    onMergeContracts: (List<Contract>) -> Unit = {},
    allFolders: List<Folder> = emptyList(),
    onMoveContracts: (List<Contract>, String?, String) -> Unit = { _, _, _ -> },
) {
    var renaming by remember { mutableStateOf(false) }
    /** null 이면 보통 화면, 아니면 분리하려고 고른 계약서 id 들 */
    var selecting by remember { mutableStateOf<Set<String>?>(null) }
    var splitting by remember { mutableStateOf(false) }
    var mergingContracts by remember { mutableStateOf(false) }
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
                val canMerge = sel.size >= 2
                Column(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(16.dp)) {
                    Text(
                        "다른 폴더(또는 새 폴더)로 보내려면 '옮기기', 같은 계약서의 앞뒷장이면 '합치기'(고른 순서대로 페이지를 붙임)를 누르세요.",
                        color = Brand.TextSub,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WhitePillButton(text = "취소", icon = Icons.Outlined.Close, onClick = { selecting = null }, height = 50.dp)
                        WhitePillButton(
                            text = "옮기기",
                            icon = Icons.AutoMirrored.Outlined.CallSplit,
                            onClick = { splitting = true },
                            enabled = sel.isNotEmpty(),
                            height = 50.dp,
                            modifier = Modifier.alpha(if (sel.isNotEmpty()) 1f else 0.4f),
                        )
                        GradientPillButton(
                            text = "합치기 (${sel.size})",
                            onClick = { mergingContracts = true },
                            enabled = canMerge,
                            showArrow = false,
                            height = 50.dp,
                            modifier = Modifier.weight(1f).alpha(if (canMerge) 1f else 0.5f),
                            leading = {
                                Icon(Icons.Outlined.CallMerge, contentDescription = null, tint = Color.White)
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
                    if (selecting == null && contracts.isNotEmpty()) {
                        TextButton(onClick = { selecting = emptySet() }) { Text("골라서 옮기기·합치기") }
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

    if (folder != null && mergingContracts) {
        // 고른 순서 그대로 (먼저 고른 계약서가 앞 페이지)
        val chosen = selecting.orEmpty().mapNotNull { id -> contracts.firstOrNull { it.id == id } }
        AlertDialog(
            onDismissRequest = { mergingContracts = false },
            title = { Text("계약서 ${chosen.size}건을 하나로 합치기") },
            text = {
                Column {
                    Text("고른 순서대로 페이지를 이어 붙여 한 계약서로 만들고, 합친 페이지로 다시 분석합니다.", color = Brand.TextSub, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    chosen.forEachIndexed { i, c ->
                        Text("${i + 1}. ${c.title.ifBlank { "계약서" }} (${c.pageCount}페이지)", fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    mergingContracts = false
                    selecting = null
                    onMergeContracts(chosen)
                }) { Text("합치기") }
            },
            dismissButton = { TextButton(onClick = { mergingContracts = false }) { Text("취소") } },
        )
    }
    if (folder != null && splitting) {
        val chosen = contracts.filter { it.id in selecting.orEmpty() }
        FolderPickerDialog(
            title = "${chosen.size}건을 어느 폴더로 옮길까요?",
            folders = allFolders,
            currentFolderId = folder.id,
            suggestedNewName = chosen.firstNotNullOfOrNull { it.analysis?.folderName?.takeIf { n -> n.isNotBlank() && n != folder.name } }.orEmpty(),
            onDismiss = { splitting = false },
            onPick = { target, newName ->
                splitting = false
                if (target == folder.id) return@FolderPickerDialog
                selecting = null
                onMoveContracts(chosen, target, newName)
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
            title = { Text("계약 상태") },
            text = {
                Column {
                    Text(
                        "보유 중이거나 임차·임대 중이면 진행중, 매도를 마쳤거나 계약이 끝났으면 완료입니다. 직접 고르면 자동으로 바뀌지 않습니다.",
                        color = Brand.TextSub,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    val options = listOf<Pair<FolderStatus?, String>>(
                        FolderStatus.ONGOING to "진행중",
                        FolderStatus.DONE to "완료",
                        null to "자동 (계약 내용으로 판단)",
                    )
                    options.forEach { (s, label) ->
                        val selected = if (s == null) !folder.statusManual else folder.statusManual && folder.status == s
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSetStatus(s); choosingStatus = false }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = { onSetStatus(s); choosingStatus = false })
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingStatus = false }) { Text("닫기") } },
        )
    }
    if (folder != null && choosingStakeholder) {
        StakeholderPickerDialog(
            current = folder.stakeholderNames,
            stakeholders = stakeholders,
            onDismiss = { choosingStakeholder = false },
            onPick = { onSetStakeholders(it); choosingStakeholder = false },
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

@OptIn(ExperimentalLayoutApi::class)
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // 상태: 눌러서 바꾸기
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (folder.status == FolderStatus.ONGOING) Brand.Blue else Brand.TextSub.copy(alpha = 0.15f))
                            .clickable(onClick = onStatus)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val color = if (folder.status == FolderStatus.ONGOING) Color.White else Brand.Navy
                        Text(folder.status.label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        if (folder.statusManual) Text(" (직접)", color = color, fontSize = 10.sp)
                        Icon(Icons.Outlined.ArrowDropDown, contentDescription = "상태 바꾸기", tint = color, modifier = Modifier.size(16.dp))
                    }
                    // 이해관계자: 계약서 당사자로 나오는 등록된 사람만 (여러 명 가능)
                    folder.stakeholderNames.forEach { name ->
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Brand.CardTint)
                                .clickable(onClick = onStakeholder)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Person, contentDescription = null, tint = Brand.Blue, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(name, color = Brand.Blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onStakeholder)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, tint = Brand.TextSub, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("이해관계자", color = Brand.TextSub, fontSize = 12.sp)
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

/** 이 폴더의 이해관계자를 고른다 (여러 명 가능). 새 이름을 입력하면 이해관계자 목록에도 추가된다. */
@Composable
fun StakeholderPickerDialog(
    current: List<String>,
    stakeholders: List<Stakeholder>,
    onDismiss: () -> Unit,
    onPick: (List<String>) -> Unit,
) {
    var chosen by remember { mutableStateOf(current.toSet()) }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이해관계자") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "보통은 계약서의 임대인·임차인(매도인·매수인) 중 등록된 이름이 자동으로 들어갑니다. 여기서 직접 고칠 수 있고, '계약서 내용적용'을 누르면 다시 자동으로 맞춰집니다.",
                    color = Brand.TextSub,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(8.dp))
                (stakeholders.map { it.name } + current).distinct().forEach { name ->
                    val toggle = { chosen = if (name in chosen) chosen - name else chosen + name }
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = toggle).padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = name in chosen, onCheckedChange = { toggle() })
                        Text(name)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(30) },
                    singleLine = true,
                    label = { Text("새 이름 추가") },
                    placeholder = { Text("예: 홍길동") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val names = (chosen + listOfNotNull(newName.trim().takeIf { it.isNotBlank() })).toList()
                onPick(names)
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
