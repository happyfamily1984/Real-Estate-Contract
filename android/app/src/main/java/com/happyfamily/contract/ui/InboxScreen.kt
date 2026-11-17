package com.happyfamily.contract.ui

import androidx.activity.compose.BackHandler
import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.happyfamily.contract.data.Contract
import com.happyfamily.contract.data.ContractRepository
import com.happyfamily.contract.data.ContractStatus
import com.happyfamily.contract.data.Folder
import com.happyfamily.contract.data.FolderRepository
import com.happyfamily.contract.data.Glance
import com.happyfamily.contract.data.SummaryText
import com.happyfamily.contract.data.formatShortDate
import com.happyfamily.contract.ui.theme.Brand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InboxViewModel(application: Application) : AndroidViewModel(application) {
    /** 아직 폴더를 확인하지 않은 새로 올린 계약서 (올린 순서). null 이면 불러오는 중 */
    val contracts: StateFlow<List<Contract>?> = ContractRepository.contractsFlow()
        .map { list -> list.filter { !it.confirmed }.sortedBy { it.createdAt } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val folders: StateFlow<List<Folder>> = FolderRepository.foldersFlow()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val message = MutableStateFlow<String?>(null)

    fun move(contracts: List<Contract>, target: String?, newName: String) = viewModelScope.launch {
        try {
            val (_, name) = FolderRepository.moveContracts(getApplication(), contracts, target, newName)
            message.value = "${contracts.size}건을 '$name' 폴더로 옮겼어요."
        } catch (e: Exception) {
            message.value = "옮기지 못했습니다: ${friendlyError(e)}"
        }
    }

    /** 제안 폴더로 넣기 (제안이 새 폴더면 이때 만든다) */
    fun confirm(contracts: List<Contract>) = viewModelScope.launch {
        try {
            val ready = contracts.filter { it.folderId != null || it.analysis != null }
            if (ready.isEmpty()) {
                message.value = "아직 분석 중이라 제안 폴더가 없어요. 분석이 끝나면 넣을 수 있어요."
                return@launch
            }
            val names = FolderRepository.confirmSuggested(getApplication(), ready)
            message.value = "${ready.size}건을 넣었어요: ${names.joinToString(", ")}"
        } catch (e: Exception) {
            message.value = friendlyError(e)
        }
    }
}

/**
 * 새로 올린 계약서 정리: 여러 파일을 올렸을 때 하나씩(또는 여러 개 골라) 원하는 폴더로 옮긴다.
 * 옮기거나 확인하면 목록에서 사라진다. 사진을 누르면 크게 보며 내용을 다시 확인할 수 있다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onBack: () -> Unit,
    onPreview: (contractId: String) -> Unit,
    onOpenContract: (contractId: String) -> Unit,
    vm: InboxViewModel = viewModel(),
) {
    val contracts by vm.contracts.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    /** 폴더 고르기 창을 띄울 대상 */
    var moving by remember { mutableStateOf<List<Contract>?>(null) }
    // 뒤로가기: 고른 것이 있으면 선택만 해제
    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    // 목록에서 사라진 것은 선택에서도 뺀다. 다 정리하면 화면을 닫는다.
    var hadItems by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(contracts) {
        val list = contracts ?: return@LaunchedEffect
        selected = selected.filter { id -> list.any { it.id == id } }.toSet()
        if (list.isNotEmpty()) hadItems = true
        if (list.isEmpty() && hadItems) {
            android.widget.Toast.makeText(context, "새로 올린 계약서를 모두 정리했어요.", android.widget.Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    /** 제안 폴더 표시: 이미 있는 폴더 이름, 또는 새 폴더 이름. 분석 중이면 null */
    val suggestion: (Contract) -> String? = { c ->
        val existing = folders.firstOrNull { it.id == (c.folderId ?: c.suggestedFolderId) }?.name
        when {
            existing != null -> existing
            c.analysis != null -> "새 폴더 '${c.analysis.folderName.ifBlank { c.title.take(12) }}'"
            else -> null
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.background(Brand.ScreenGradient),
        topBar = {
            TopAppBar(
                title = { Text("새로 올린 계약서 정리", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로") }
                },
                actions = {
                    val list = contracts.orEmpty()
                    if (list.isNotEmpty()) {
                        TextButton(onClick = {
                            selected = if (selected.size == list.size) emptySet() else list.map { it.id }.toSet()
                        }) { Text(if (selected.size == list.size) "선택 해제" else "모두 선택") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = Brand.Navy),
            )
        },
        bottomBar = {
            if (selected.isNotEmpty()) {
                val chosen = contracts.orEmpty().filter { it.id in selected }
                Row(
                    Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(onClick = { vm.confirm(chosen) }, modifier = Modifier.weight(1f)) {
                        Text("고른 ${chosen.size}건 제안대로")
                    }
                    Button(onClick = { moving = chosen }, modifier = Modifier.weight(1f)) { Text("고른 ${chosen.size}건 옮기기") }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val list = contracts
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding)) {
                CenterText("정리할 새 계약서가 없어요.\n모두 폴더에 들어갔습니다.")
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        "AI 가 같은 부동산 폴더를 제안해 두었어요. 맞으면 '제안 폴더로 넣기', 다르면 '다른 폴더로'를 누르세요. 여러 개는 길게 눌러 고른 뒤 아래 버튼으로 한 번에 옮길 수 있어요. 넣은 계약서는 목록에서 사라지고, 사진을 누르면 크게 볼 수 있어요.",
                        color = Brand.TextSub,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    val ready = list.count { it.folderId != null || it.analysis != null }
                    Button(
                        onClick = { vm.confirm(list) },
                        enabled = ready > 0,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text("전체 ${ready}건 제안 폴더로 넣기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    if (ready < list.size) {
                        Text("분석 중인 ${list.size - ready}건은 끝나면 넣을 수 있어요.", color = Brand.TextSub, fontSize = 12.sp)
                    }
                }
                items(list, key = { it.id }) { c ->
                    InboxCard(
                        contract = c,
                        suggested = suggestion(c),
                        selected = c.id in selected,
                        onToggle = { selected = if (c.id in selected) selected - c.id else selected + c.id },
                        onPreview = { onPreview(c.id) },
                        onOpen = { onOpenContract(c.id) },
                        onConfirm = { vm.confirm(listOf(c)) },
                        onMove = { moving = listOf(c) },
                        selecting = selected.isNotEmpty(),
                    )
                }
            }
        }
    }

    moving?.let { targets ->
        FolderPickerDialog(
            title = if (targets.size == 1) "어느 폴더로 옮길까요?" else "${targets.size}건을 어느 폴더로 옮길까요?",
            folders = folders,
            currentFolderId = targets.map { it.folderId ?: it.suggestedFolderId }.distinct().singleOrNull(),
            suggestedNewName = targets.firstNotNullOfOrNull { it.analysis?.folderName?.takeIf { n -> n.isNotBlank() } }.orEmpty(),
            onDismiss = { moving = null },
            onPick = { target, newName ->
                moving = null
                selected = selected - targets.map { it.id }.toSet()
                vm.move(targets, target, newName)
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun InboxCard(
    contract: Contract,
    suggested: String?,
    selected: Boolean,
    onToggle: () -> Unit,
    onPreview: () -> Unit,
    onOpen: () -> Unit,
    onConfirm: () -> Unit,
    onMove: () -> Unit,
    selecting: Boolean = false,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            // 길게 누르면 선택, 선택 중에는 누르기만 해도 선택/해제
            .combinedClickable(onClick = { if (selecting) onToggle() else onOpen() }, onLongClick = onToggle),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) Brand.CardTint else Color.White),
        border = if (selected) BorderStroke(2.dp, Brand.Blue) else null,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = "선택",
                    tint = if (selected) Brand.Blue else Brand.Divider,
                    modifier = Modifier.size(28.dp).clickable(onClick = onToggle),
                )
                Spacer(Modifier.width(8.dp))
                // 큰 사진으로 보기
                Box(
                    Modifier
                        .size(width = 84.dp, height = 112.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brand.CardTint)
                        .clickable(onClick = onPreview),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    contract.thumbnail?.let {
                        AsyncImage(model = it, contentDescription = "크게 보기", modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    }
                    Icon(
                        Icons.Outlined.ZoomIn, contentDescription = null, tint = Color.White,
                        modifier = Modifier.padding(4.dp).background(Brand.Navy.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(2.dp).size(18.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    val a = contract.analysis
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (contract.contractType.isNotBlank()) {
                            Badge(SummaryText.kindLabel(contract.contractType), Brand.Violet)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("${contract.pageCount}페이지", color = Brand.TextSub, fontSize = 12.sp)
                    }
                    Text(
                        contract.title.ifBlank { "분석 중인 계약서" },
                        color = Brand.Navy,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (a != null) {
                        if (a.contractDate.isNotBlank()) Text("계약 ${formatShortDate(a.contractDate)}", color = Brand.TextSub, fontSize = 12.sp)
                        val money = Glance.amounts(a).joinToString(" · ") { (l, v) -> "$l $v" }
                        if (money.isNotBlank()) Text(money, color = Brand.Blue, fontSize = 13.sp)
                        Glance.parties(a)?.let { Text(it, color = Brand.TextSub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    } else {
                        Text(
                            if (contract.status == ContractStatus.ERROR) "분석 실패 — 눌러서 확인" else "AI 가 분석하고 있어요",
                            color = Brand.TextSub,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (suggested != null) "제안: $suggested" else "분석이 끝나면 폴더를 제안해요",
                    color = if (suggested != null) Brand.Navy else Brand.TextSub,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 여러 개를 고르는 중에는 카드 버튼을 숨기고 아래 '고른 n건' 버튼으로 한꺼번에 처리한다.
            if (!selecting) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onConfirm, enabled = suggested != null, modifier = Modifier.weight(1f)) { Text("옮기기") }
                OutlinedButton(onClick = onMove, modifier = Modifier.weight(1f)) { Text("다른 폴더로 옮기기") }
            }
        }
    }
}
