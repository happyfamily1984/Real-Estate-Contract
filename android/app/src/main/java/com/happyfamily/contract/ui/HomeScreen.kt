package com.happyfamily.contract.ui

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.happyfamily.contract.R
import com.happyfamily.contract.data.AuthRepository
import com.happyfamily.contract.data.Folder
import com.happyfamily.contract.ui.theme.Brand
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    userEmail: String,
    onOpenFolder: (String) -> Unit,
    onOpenContract: (String) -> Unit,
    onOpenStakeholders: () -> Unit,
    onOpenAiSettings: () -> Unit = {},
    onOpenSchedule: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
    importVm: ImportViewModel = viewModel(),
) {
    val activity = LocalContext.current as Activity
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.stakeholderFilter.collectAsStateWithLifecycle()
    val loadError by viewModel.loadError.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val sortDesc by viewModel.sortDesc.collectAsStateWithLifecycle()
    val upload by importVm.upload.collectAsStateWithLifecycle()
    val message by importVm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmSignOut by remember { mutableStateOf(false) }
    /** null 이면 보통 화면, 아니면 합치기 위해 고른 폴더 id 들 */
    var selecting by remember { mutableStateOf<Set<String>?>(null) }
    var mergeTargets by remember { mutableStateOf<List<Folder>?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            importVm.consumeMessage()
        }
    }

    val importer = rememberImporter(importVm, folderId = null, onCreated = onOpenContract)

    HomeContent(
        state = state,
        query = query,
        onQueryChange = { viewModel.query.value = it },
        stakeholderFilter = filter,
        onStakeholderFilter = { viewModel.stakeholderFilter.value = it },
        upload = upload,
        loadError = loadError,
        snackbar = snackbar,
        onPickFiles = importer.pickFiles,
        onScan = importer.scan,
        onStakeholders = onOpenStakeholders,
        onAiSettings = onOpenAiSettings,
        onSchedule = onOpenSchedule,
        viewMode = viewMode,
        onViewMode = viewModel::setViewMode,
        sortMode = sortMode,
        sortDesc = sortDesc,
        onSort = viewModel::setSort,
        onToggleSortDirection = viewModel::toggleSortDirection,
        onSignOut = { confirmSignOut = true },
        onOpenFolder = onOpenFolder,
        onOpenContract = onOpenContract,
        onRestartAnalysis = {
            viewModel.restartAnalysis()
            scope.launch { snackbar.showSnackbar("분석을 처음부터 다시 시작했어요.") }
        },
        selecting = selecting,
        onStartSelect = { first -> selecting = setOfNotNull(first) },
        onToggleSelect = { id -> selecting = selecting?.let { if (id in it) it - id else it + id } },
        onCancelSelect = { selecting = null },
        onMerge = {
            val all = state?.let { it.ongoing + it.done }.orEmpty()
            mergeTargets = all.filter { it.id in selecting.orEmpty() }
        },
    )

    mergeTargets?.let { folders ->
        MergeDialog(
            folders = folders,
            onDismiss = { mergeTargets = null },
            onConfirm = { target, name ->
                mergeTargets = null
                selecting = null
                viewModel.merge(target.id, folders.map { it.id }, name) { msg ->
                    scope.launch { snackbar.showSnackbar(msg) }
                }
            },
        )
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("로그아웃") },
            text = { Text("$userEmail 계정에서 로그아웃할까요?\n저장된 계약서는 클라우드에 그대로 남아 있습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    scope.launch { AuthRepository.signOut(activity) }
                }) { Text("로그아웃") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("취소") } },
        )
    }
}

/** 화면 모양만 그리는 부분 (스크린샷 테스트에서도 사용). */
@Composable
internal fun HomeContent(
    state: HomeState?,
    query: String,
    onQueryChange: (String) -> Unit,
    stakeholderFilter: String?,
    onStakeholderFilter: (String?) -> Unit,
    upload: UploadState?,
    loadError: String?,
    snackbar: SnackbarHostState,
    onPickFiles: () -> Unit,
    onScan: () -> Unit,
    onStakeholders: () -> Unit,
    onAiSettings: () -> Unit = {},
    onSchedule: () -> Unit = {},
    viewMode: ViewMode = ViewMode.CARD,
    onViewMode: (ViewMode) -> Unit = {},
    sortMode: SortMode = SortMode.NAME,
    sortDesc: Boolean = false,
    onSort: (SortMode) -> Unit = {},
    onToggleSortDirection: () -> Unit = {},
    onSignOut: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenContract: (String) -> Unit,
    onRestartAnalysis: () -> Unit = {},
    selecting: Set<String>? = null,
    onStartSelect: (String?) -> Unit = {},
    onToggleSelect: (String) -> Unit = {},
    onCancelSelect: () -> Unit = {},
    onMerge: () -> Unit = {},
) {
    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.background(Brand.ScreenGradient),
        topBar = {
            Box(Modifier.fillMaxWidth()) {
                // 시안의 오른쪽 위 건물 장식
                Image(
                    painter = painterResource(R.drawable.header_buildings),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .width(200.dp)
                        .offset(y = 14.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 24.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "내 계약서",
                        color = Brand.Navy,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f),
                    )
                    HeaderIcon(Icons.Outlined.EventNote, "향후 일정", onClick = onSchedule)
                    HeaderIcon(Icons.Outlined.Groups, "이해관계자", onClick = onStakeholders)
                    HeaderIcon(Icons.Outlined.Settings, "AI 설정", onClick = onAiSettings)
                    HeaderIcon(Icons.AutoMirrored.Outlined.Logout, "로그아웃", onClick = onSignOut)
                }
            }
        },
        floatingActionButton = {
            if (selecting != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WhitePillButton(text = "취소", icon = Icons.Outlined.Close, onClick = onCancelSelect)
                    GradientPillButton(
                        text = "합치기 (${selecting.size})",
                        onClick = onMerge,
                        enabled = selecting.size >= 2,
                        showArrow = false,
                        modifier = Modifier.alpha(if (selecting.size >= 2) 1f else 0.5f),
                        leading = {
                            Icon(Icons.Outlined.CallMerge, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                        },
                    )
                }
            } else if (upload == null) {
                // 휴대폰에 있는 PDF·JPG 파일 업로드 + 카메라로 촬영
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WhitePillButton(text = "파일 업로드", icon = Icons.Outlined.UploadFile, onClick = onPickFiles)
                    GradientPillButton(
                        text = "계약서 촬영",
                        onClick = onScan,
                        showArrow = false,
                        leading = {
                            Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                        },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = "폴더, 주소, 이름, 메모로 검색",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            upload?.let { UploadProgress(it, Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) }

            when {
                loadError != null -> CenterText("목록을 불러오지 못했습니다.\n$loadError")
                state == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.isEmpty -> EmptyState()
                else -> {
                    if (selecting != null) {
                        Text(
                            "같은 부동산 폴더를 2개 이상 고르세요.",
                            color = Brand.Blue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                        )
                    } else {
                        StakeholderChips(state, stakeholderFilter, onStakeholderFilter, onStakeholders)
                    }
                    val nothing = state.unfiled.isEmpty() && state.ongoing.isEmpty() && state.done.isEmpty()
                    if (nothing) {
                        CenterText("검색 결과가 없습니다.")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 112.dp),
                            verticalArrangement = Arrangement.spacedBy(if (viewMode == ViewMode.CARD) 12.dp else 4.dp),
                        ) {
                            item(key = "view-toggle") {
                                SortAndViewBar(sortMode, sortDesc, onSort, onToggleSortDirection, viewMode, onViewMode)
                            }
                            if (state.unfiled.isNotEmpty()) {
                                sectionHeader("분석 중인 계약서", state.unfiled.size, actionLabel = "분석 다시 시작", onAction = onRestartAnalysis)
                                items(state.unfiled, key = { "c-" + it.id }) { c ->
                                    ContractCard(c, onClick = { onOpenContract(c.id) })
                                }
                            }
                            val folderClick: (Folder) -> Unit = { f ->
                                if (selecting != null) onToggleSelect(f.id) else onOpenFolder(f.id)
                            }
                            val canMerge = selecting == null && state.ongoing.size + state.done.size >= 2
                            folderSection(
                                "진행중", state.ongoing, state.contractCounts, selecting, folderClick, onStartSelect, viewMode,
                                actionLabel = if (canMerge) "폴더 합치기" else null,
                            )
                            folderSection(
                                "완료", state.done, state.contractCounts, selecting, folderClick, onStartSelect, viewMode,
                                actionLabel = if (canMerge && state.ongoing.isEmpty()) "폴더 합치기" else null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = description, tint = Brand.Navy, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun StakeholderChips(
    state: HomeState,
    selected: String?,
    onSelect: (String?) -> Unit,
    onStakeholders: () -> Unit,
) {
    if (state.stakeholders.isEmpty()) {
        // 이해관계자가 없으면 입력을 권한다.
        Row(
            Modifier
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Brand.CardTint)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "이해관계자(본인) 이름을 입력하면 그 사람 기준으로 진행중·완료를 정리해요.",
                color = Brand.Navy,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onStakeholders) { Text("입력") }
        }
        return
    }
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.White,
            selectedContainerColor = Brand.Blue,
            selectedLabelColor = Color.White,
        )
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("전체") }, colors = colors)
        state.stakeholders.forEach { s ->
            FilterChip(
                selected = selected == s.name,
                onClick = { onSelect(s.name) },
                label = { Text(s.name) },
                colors = colors,
            )
        }
    }
}

private fun LazyListScope.sectionHeader(
    title: String,
    count: Int,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    item(key = "h-$title") {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Brand.Navy, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text("$count", color = Brand.Blue, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

private fun LazyListScope.folderSection(
    title: String,
    folders: List<Folder>,
    counts: Map<String, Int>,
    selecting: Set<String>?,
    onClick: (Folder) -> Unit,
    onStartSelect: (String?) -> Unit,
    viewMode: ViewMode,
    actionLabel: String? = null,
) {
    if (folders.isEmpty()) return
    sectionHeader(title, folders.size, actionLabel = actionLabel, onAction = { onStartSelect(null) })
    val longClick: (Folder) -> Unit = { f -> if (selecting == null) onStartSelect(f.id) else onClick(f) }
    when (viewMode) {
        ViewMode.LIST -> items(folders, key = { "f-" + it.id }) { f ->
            FolderRow(f, counts[f.id] ?: 0, { onClick(f) }, { longClick(f) }, selecting?.contains(f.id))
        }
        // 바둑판: 한 줄에 두 개씩
        ViewMode.GRID -> items(folders.chunked(2), key = { "g-" + it.first().id }) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                row.forEach { f ->
                    Box(Modifier.weight(1f)) {
                        FolderTile(f, counts[f.id] ?: 0, { onClick(f) }, { longClick(f) }, selecting?.contains(f.id))
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        ViewMode.CARD -> items(folders, key = { "f-" + it.id }) { f ->
            // 길게 누르면 합치기 선택을 시작한다.
            FolderCard(f, counts[f.id] ?: 0, { onClick(f) }, { longClick(f) }, selecting?.contains(f.id))
        }
    }
}

/** 정렬 기준(이름순·취득날짜순·최근계약순)과 방향(↑↓), 보기 방식(사진·목록·바둑판) */
@Composable
private fun SortAndViewBar(
    sortMode: SortMode,
    sortDesc: Boolean,
    onSort: (SortMode) -> Unit,
    onToggleDirection: () -> Unit,
    viewMode: ViewMode,
    onViewMode: (ViewMode) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("정렬", color = Brand.TextSub, fontSize = 12.sp)
        SortMode.entries.forEach { m ->
            val selected = m == sortMode
            Text(
                m.label,
                color = if (selected) Brand.Blue else Brand.TextSub,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) Brand.CardTint else Color.Transparent)
                    .clickable { onSort(m) }
                    .padding(horizontal = 9.dp, vertical = 6.dp),
            )
        }
        IconButton(onClick = onToggleDirection, modifier = Modifier.size(34.dp)) {
            Icon(
                if (sortDesc) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
                contentDescription = if (sortDesc) "내림차순 (눌러서 거꾸로)" else "오름차순 (눌러서 거꾸로)",
                tint = Brand.Blue,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        listOf(
            ViewMode.CARD to (Icons.Outlined.ViewAgenda to "사진 포함"),
            ViewMode.LIST to (Icons.AutoMirrored.Outlined.ViewList to "목록"),
            ViewMode.GRID to (Icons.Outlined.GridView to "바둑판"),
        ).forEach { (mode, iconLabel) ->
            val selected = mode == viewMode
            IconButton(
                onClick = { onViewMode(mode) },
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) Brand.CardTint else Color.Transparent),
            ) {
                Icon(
                    iconLabel.first,
                    contentDescription = iconLabel.second + " 보기",
                    tint = if (selected) Brand.Blue else Brand.TextSub,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** 합칠 폴더들 중 남길 폴더(이름)를 고른다. */
@Composable
private fun MergeDialog(folders: List<Folder>, onDismiss: () -> Unit, onConfirm: (Folder, String) -> Unit) {
    var target by remember { mutableStateOf(folders.first()) }
    var name by remember { mutableStateOf(folders.first().name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("폴더 ${folders.size}개 합치기") },
        text = {
            Column {
                Text(
                    "고른 폴더의 계약서를 모두 한 폴더로 모읍니다. 남길 폴더를 고르세요 (이해관계자·상태는 이 폴더 것을 씁니다).",
                    color = Brand.TextSub,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(8.dp))
                folders.forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().clickable { target = f; name = f.name }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = target.id == f.id, onClick = { target = f; name = f.name })
                        Column {
                            Text(f.name)
                            if (f.address.isNotBlank()) Text(f.address, color = Brand.TextSub, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    singleLine = true,
                    label = { Text("합친 폴더 이름") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(target, name.trim()) }, enabled = name.isNotBlank()) { Text("합치기") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
