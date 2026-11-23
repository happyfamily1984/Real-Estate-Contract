package com.happyfamily.contract.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.RadioButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.happyfamily.contract.data.Folder
import com.happyfamily.contract.data.FolderRepository
import com.happyfamily.contract.data.SummaryWorker
import com.happyfamily.contract.ui.theme.Brand
import com.happyfamily.contract.data.AnalyzeWorker
import androidx.compose.material3.OutlinedButton
import com.happyfamily.contract.data.Contract
import com.happyfamily.contract.data.Glance
import com.happyfamily.contract.data.ContractAnalysis
import com.happyfamily.contract.data.ContractRepository
import com.happyfamily.contract.data.ContractStatus
import com.happyfamily.contract.data.Party
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContractDetailScreen(
    contractId: String,
    onBack: () -> Unit,
    onOpenPhoto: (String, Int) -> Unit,
    onOpenFolder: (String) -> Unit,
) {
    val flow = remember(contractId) { ContractRepository.contractFlow(contractId) }
    val loaded by flow.collectAsStateWithLifecycle(initialValue = null)
    val foldersFlow = remember { FolderRepository.foldersFlow() }
    val folders by foldersFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var moving by remember { mutableStateOf(false) }
    var moveMessage by remember { mutableStateOf<String?>(null) }
    /** null 이면 보통 화면, 아니면 옮기려고 고른 페이지 번호들 */
    var pageSel by remember { mutableStateOf<Set<Int>?>(null) }
    var movingPages by remember { mutableStateOf(false) }
    // 뒤로가기: 페이지 고르는 중이면 고르기만 취소
    BackHandler(enabled = pageSel != null) { pageSel = null }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var renamingDoc by remember { mutableStateOf(false) }
    /** true: 계약관련서류로 바꾸기 확인, false: 계약서로 바꾸기 확인, null: 안 물음 */
    var confirmKind by remember { mutableStateOf<Boolean?>(null) }

    val contract = loaded

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        contract?.title.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    if (contract != null && !contract.isRelated &&
                        (contract.status == ContractStatus.DONE || contract.status == ContractStatus.ERROR)
                    ) {
                        IconButton(onClick = {
                            scope.launch {
                                runCatching { ContractRepository.reanalyze(context, contract.id) }
                                    .onFailure { actionError = "재분석 요청 실패: ${it.message}" }
                            }
                        }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "다시 분석")
                        }
                    }
                    IconButton(onClick = { confirmDelete = true }, enabled = contract != null) {
                        Icon(Icons.Outlined.Delete, contentDescription = "삭제")
                    }
                },
            )
        },
    ) { padding ->
        if (contract == null || deleting) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            moveMessage?.let { Text(it, color = Brand.Blue, fontWeight = FontWeight.SemiBold) }

            PhotoStrip(
                contract, onOpenPhoto,
                selected = pageSel,
                onToggle = { i -> pageSel = (pageSel ?: emptySet()).let { if (i in it) it - i else it + i } },
            )
            // 페이지(파일) 단위로 옮기기: 하나씩 고르거나 전체
            val busy = !contract.isRelated &&
                (contract.status == ContractStatus.PENDING || contract.status == ContractStatus.PROCESSING)
            val sel = pageSel
            if (sel == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { pageSel = emptySet() }, enabled = !busy && contract.pageCount > 1, modifier = Modifier.weight(1f)) {
                        Text("페이지 골라서 옮기기")
                    }
                    OutlinedButton(onClick = { moving = true }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("전체 옮기기") }
                }
            } else {
                Text(
                    "옮길 페이지를 누르세요. 옮긴 페이지는 위 목록에서 사라지고, 옮겨 간 폴더에 새 계약서로 들어갑니다.",
                    color = Brand.TextSub,
                    fontSize = 13.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { pageSel = null }) { Text("취소") }
                    OutlinedButton(onClick = { pageSel = (0 until contract.pageCount).toSet() }, modifier = Modifier.weight(1f)) { Text("전체 선택") }
                    Button(onClick = { movingPages = true }, enabled = sel.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        Text("고른 ${sel.size}장 옮기기")
                    }
                }
            }

            // 새로 올린 계약서(확인 전)는 AI 제안 폴더를 보여 주고 '넣기'로 확정
            val suggestion = if (!contract.confirmed) {
                val existing = folders.firstOrNull { it.id == (contract.folderId ?: contract.suggestedFolderId) }?.name
                existing ?: contract.analysis?.let { "새 폴더 '${it.folderName.ifBlank { contract.title.take(12) }}'" }
            } else {
                null
            }
            FolderRow(
                folder = folders.firstOrNull { it.id == contract.folderId },
                analyzing = !contract.isRelated && contract.status != ContractStatus.DONE && contract.status != ContractStatus.ERROR,
                suggestion = suggestion,
                onOpen = { contract.folderId?.let(onOpenFolder) },
                onMove = { moving = true },
                onConfirm = {
                    scope.launch {
                        runCatching { FolderRepository.confirmSuggested(context, listOf(contract)) }
                            .onSuccess { moveMessage = "'${it.joinToString()}' 폴더에 넣었어요." }
                            .onFailure { actionError = friendlyError(it) }
                    }
                },
            )

            if (contract.isRelated) {
                RelatedDocCard(
                    contract = contract,
                    onRename = { renamingDoc = true },
                    onMakeContract = { confirmKind = false },
                )
            } else when (contract.status) {
                ContractStatus.PENDING, ContractStatus.PROCESSING -> {
                    val text = buildString {
                        append(
                            if (contract.status == ContractStatus.PENDING) {
                                "분석 대기 중이에요. 올린 순서대로 하나씩 분석합니다."
                            } else {
                                "AI가 계약서를 읽고 있어요. 보통 30초~1분 걸립니다."
                            },
                        )
                        append("\n다른 화면으로 가도 백그라운드에서 계속 처리됩니다.")
                        contract.error?.let { append("\n\n").append(it) }
                    }
                    StatusCard(text, loading = true)
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                runCatching { AnalyzeWorker.recoverStuck(context, force = true) }
                                    .onFailure { actionError = "다시 시작하지 못했습니다: ${it.message}" }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("오래 멈춰 있으면: 분석 다시 시작") }
                }
                ContractStatus.ERROR -> {
                    StatusCard("분석에 실패했습니다.\n${contract.error.orEmpty()}", loading = false)
                    Button(
                        onClick = { scope.launch { runCatching { ContractRepository.reanalyze(context, contract.id) } } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("다시 분석") }
                }
                ContractStatus.DONE -> contract.analysis?.let { a ->
                    if (a.isLocal) {
                        StatusCard(
                            "AI 를 쓸 수 없어서 휴대폰이 글자를 읽어 만든 기본 결과입니다. 금액·날짜·이름을 원본과 꼭 대조하세요.\n" +
                                "AI 를 다시 쓸 수 있게 되면 자동으로 다시 분석합니다.",
                            loading = false,
                        )
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    runCatching { ContractRepository.reanalyze(context, contract.id) }
                                        .onFailure { actionError = "재분석 요청 실패: ${it.message}" }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("지금 AI 로 다시 분석") }
                    }
                    AnalysisSections(a)
                }
            }

            if (!contract.isRelated) {
                // 계약서로 스캔했지만 계약서가 아닌 서류(채권채무존재확인서 등)
                OutlinedButton(onClick = { confirmKind = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("계약서가 아니면: 계약관련서류로 바꾸기")
                }
            }

            MemoSection(contract)
        }
    }

    if (movingPages && contract != null) {
        val chosen = pageSel.orEmpty().sorted()
        FolderPickerDialog(
            title = "${chosen.size}장을 어느 폴더로 옮길까요?",
            folders = folders,
            currentFolderId = null,
            suggestedNewName = "",
            onDismiss = { movingPages = false },
            onPick = { target, newName ->
                movingPages = false
                pageSel = null
                scope.launch {
                    runCatching { FolderRepository.movePages(context, contract, chosen, target, newName) }
                        .onSuccess {
                            actionError = null
                            moveMessage = "${chosen.size}장을 '$it' 폴더로 옮겼어요." +
                                if (contract.isRelated) "" else " 옮긴 페이지와 남은 페이지를 다시 분석합니다."
                        }
                        .onFailure { actionError = "옮기지 못했습니다: ${friendlyError(it)}" }
                }
            },
        )
    }

    if (moving && contract != null) {
        FolderPickerDialog(
            title = "폴더 옮기기",
            folders = folders,
            currentFolderId = contract.folderId ?: contract.suggestedFolderId,
            suggestedNewName = contract.analysis?.folderName.orEmpty(),
            onDismiss = { moving = false },
            onPick = { target, newName ->
                moving = false
                if (target != null && target == contract.folderId && contract.confirmed) return@FolderPickerDialog
                scope.launch {
                    runCatching { FolderRepository.moveContracts(context, listOf(contract), target, newName) }
                        .onSuccess { (_, name) -> actionError = null; moveMessage = "'$name' 폴더로 옮겼어요." }
                        .onFailure { actionError = "옮기지 못했습니다: ${friendlyError(it)}" }
                }
            },
        )
    }

    if (renamingDoc && contract != null) {
        TextInputDialog(
            title = "서류 이름",
            initial = contract.title,
            hint = "예: 채권채무존재확인서",
            onDismiss = { renamingDoc = false },
            onConfirm = { name ->
                renamingDoc = false
                scope.launch {
                    runCatching { ContractRepository.rename(contract.id, name) }
                        .onFailure { actionError = friendlyError(it) }
                }
            },
        )
    }

    confirmKind?.let { toRelated ->
        if (contract == null) return@let
        AlertDialog(
            onDismissRequest = { confirmKind = null },
            title = { Text(if (toRelated) "계약관련서류로 바꿀까요?" else "계약서로 바꿀까요?") },
            text = {
                Text(
                    if (toRelated) {
                        "AI 분석 결과를 지우고 계약내용정리·향후 일정에서 뺍니다. 사진은 그대로 남고 AI 분석은 하지 않아요."
                    } else {
                        "계약서로 바꾸고 AI 분석을 다시 합니다. 분석이 끝나면 계약내용정리에 들어갑니다."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmKind = null
                    scope.launch {
                        runCatching { ContractRepository.setRelated(context, listOf(contract.id), toRelated) }
                            .onSuccess {
                                actionError = null
                                moveMessage = if (toRelated) "계약관련서류로 바꿨어요." else "계약서로 바꿨어요. AI 분석을 시작합니다."
                                contract.folderId?.let { SummaryWorker.enqueue(context, it, force = false) }
                            }
                            .onFailure { actionError = friendlyError(it) }
                    }
                }) { Text("바꾸기") }
            },
            dismissButton = { TextButton(onClick = { confirmKind = null }) { Text("취소") } },
        )
    }

    if (confirmDelete && contract != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("계약서 삭제") },
            text = { Text("이 계약서와 사진을 클라우드에서 완전히 삭제할까요?\n되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    deleting = true
                    scope.launch {
                        runCatching { ContractRepository.delete(contract) }
                            .onSuccess {
                                // 폴더의 계약내용정리도 새로 맞춘다.
                                contract.folderId?.let { SummaryWorker.enqueue(context, it, force = false) }
                                onBack()
                            }
                            .onFailure {
                                deleting = false
                                actionError = "삭제 실패: ${it.message}"
                            }
                    }
                }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun FolderRow(
    folder: Folder?,
    analyzing: Boolean,
    suggestion: String?,
    onOpen: () -> Unit,
    onMove: () -> Unit,
    onConfirm: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Folder, contentDescription = null, tint = Brand.Blue)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("계약 폴더", color = Brand.TextSub, fontSize = 12.sp)
                    Text(
                        folder?.name ?: when {
                            analyzing -> "분석이 끝나면 폴더를 제안해요"
                            suggestion != null -> "아직 폴더에 넣지 않았어요"
                            else -> "폴더 없음"
                        },
                        color = Brand.Navy,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (folder != null) TextButton(onClick = onOpen) { Text("열기") }
                TextButton(onClick = onMove, enabled = !analyzing) { Text(if (folder == null) "다른 폴더로" else "옮기기") }
            }
            if (suggestion != null) {
                Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) { Text("제안 폴더로 넣기: $suggestion") }
            }
        }
    }
}

/** 페이지 목록. [selected] 가 null 이 아니면 고르는 중 (누르면 선택/해제), 길게 누르면 고르기 시작 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PhotoStrip(
    contract: Contract,
    onOpenPhoto: (String, Int) -> Unit,
    selected: Set<Int>? = null,
    onToggle: (Int) -> Unit = {},
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(contract.pageCount) { index ->
            val jpeg by produceState<ByteArray?>(null, contract.id, index, contract.pageCount) {
                value = runCatching { ContractRepository.pageImage(contract.id, index) }.getOrNull()
            }
            val isSelected = selected?.contains(index) == true
            Box(
                Modifier
                    .size(width = 96.dp, height = 128.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(if (isSelected) Modifier.border(3.dp, Brand.Blue, RoundedCornerShape(8.dp)) else Modifier)
                    .combinedClickable(
                        onClick = { if (selected != null) onToggle(index) else onOpenPhoto(contract.id, index) },
                        onLongClick = { onToggle(index) },
                    ),
                contentAlignment = Alignment.BottomEnd,
            ) {
                jpeg?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = "${index + 1}페이지",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (selected != null) {
                    Icon(
                        if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = if (isSelected) "선택됨" else "선택 안 됨",
                        tint = if (isSelected) Brand.Blue else Color.White,
                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(24.dp),
                    )
                }
                Text(
                    "${index + 1}",
                    modifier = Modifier
                        .padding(4.dp)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp),
                    color = MaterialTheme.colorScheme.surface,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun StatusCard(text: String, loading: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
            }
            Text(text)
        }
    }
}

@Composable
private fun AnalysisSections(a: ContractAnalysis) {
    val ownerLabel = a.ownerLabel
    val userLabel = a.userLabel

    // 한눈에 보기: 소재지, 계약날짜, 계약기간, 금액, 계약자만
    Section("계약서 한눈에 보기") {
        Glance.items(a).forEach { item -> GlanceRow(item.label, item.value) }
    }

    // 나머지는 접어 둔다.
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        Text(if (expanded) "자세히 접기 ▲" else "자세히 보기 (요약·확인 필요·지급 일정·특약 등) ▼")
    }
    if (!expanded) return

    if (a.summary.isNotBlank()) {
        Section("요약") { Text(a.summary, style = MaterialTheme.typography.bodyLarge) }
    }

    if (a.cautions.isNotEmpty()) {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("확인이 필요한 부분", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                a.cautions.forEach { Text("• $it", modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }
    }

    Section("기본 정보") {
        Field("계약 종류", a.contractType)
        Field("부동산 종류", a.propertyType)
        Field("소재지", a.propertyAddress)
        Field("면적", a.area)
        Field("계약일", formatDate(a.contractDate))
        if (a.acquisitionDate.isNotBlank()) {
            Field("취득일", formatDate(a.acquisitionDate) + if (a.acquisitionDateEstimated) " (예상)" else "")
        }
        if (a.isSale) {
            Field("인도일", formatDate(a.leaseStart))
        } else {
            Field("계약 기간", "${formatDate(a.leaseStart)} ~ ${formatDate(a.leaseEnd)}")
        }
    }

    Section("금액") {
        if (a.isSale) {
            val label = when (a.contractType) {
                "분양" -> "분양금액"
                "경매" -> "매각대금"
                else -> "매매대금"
            }
            Field(label, formatWonDetailed(a.salePrice))
            if (a.acquisitionAmount > 0 && a.acquisitionAmount != a.salePrice) {
                Field("취득금액", formatWonDetailed(a.acquisitionAmount))
            }
        } else {
            Field("보증금", formatWonDetailed(a.deposit))
            if (a.monthlyRent > 0) Field("월세", formatWonDetailed(a.monthlyRent))
        }
        if (a.managementFee > 0) Field("관리비", formatWonDetailed(a.managementFee))
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        PaymentField("계약금", a.downPayment.amount, a.downPayment.date)
        a.middlePayments.forEachIndexed { i, p ->
            PaymentField(if (a.middlePayments.size > 1) "중도금 ${i + 1}차" else "중도금", p.amount, p.date)
        }
        PaymentField("잔금", a.balancePayment.amount, a.balancePayment.date)
    }

    Section("당사자") {
        PartyBlock(ownerLabel, a.lessor)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        PartyBlock(userLabel, a.lessee)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("공인중개사", fontWeight = FontWeight.SemiBold)
        Field("사무소", a.agent.officeName)
        Field("대표", a.agent.representative)
        Field("등록번호", a.agent.registrationNumber)
        Field("전화", a.agent.phone)
        Field("주소", a.agent.address)
    }

    if (a.specialTerms.isNotEmpty()) {
        Section("특약사항") {
            a.specialTerms.forEachIndexed { i, term ->
                Text("${i + 1}. $term", modifier = Modifier.padding(vertical = 3.dp))
            }
        }
    }
}

@Composable
private fun GlanceRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, modifier = Modifier.width(76.dp), color = Brand.TextSub, fontSize = 15.sp)
        Text(highlightSummary(value), modifier = Modifier.weight(1f), color = Brand.Navy, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PartyBlock(label: String, p: Party) {
    Text(label, fontWeight = FontWeight.SemiBold)
    Field("성명", p.name)
    Field("전화", p.phone)
    Field("주소", p.address)
}

@Composable
private fun PaymentField(label: String, amount: Long, date: String) {
    if (amount <= 0 && date.isBlank()) return
    val dateText = if (date.isBlank()) "" else " · ${formatDate(date)}"
    Field(label, formatWon(amount) + dateText)
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    if (value.isBlank() || value == "-") return
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            modifier = Modifier.width(88.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MemoSection(contract: Contract) {
    val scope = rememberCoroutineScope()
    var text by remember(contract.id) { mutableStateOf(contract.memo) }
    var saved by remember(contract.id) { mutableStateOf(contract.memo) }

    // 다른 기기에서 메모가 바뀌었고 이 기기에서 편집 중이 아니면 반영한다.
    LaunchedEffect(contract.memo) {
        if (text == saved) text = contract.memo
        saved = contract.memo
    }

    Section("메모") {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("예: 집주인 연락은 오후에, 보증보험 가입 예정") },
            minLines = 3,
        )
        if (text != saved) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                val memo = text
                scope.launch {
                    runCatching { ContractRepository.updateMemo(contract.id, memo) }
                        .onSuccess { saved = memo }
                }
            }) { Text("메모 저장") }
        }
    }
}

/** 계약관련서류(채권채무존재확인서 등): AI 분석 없이 사진·이름·메모만 보관 */
@Composable
private fun RelatedDocCard(contract: Contract, onRename: () -> Unit, onMakeContract: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("계약관련서류 · AI 분석 안 함", color = Brand.TextSub, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    contract.title.ifBlank { "계약관련서류" },
                    color = Brand.Navy,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRename) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("이름 고치기")
                }
            }
            Text(
                "${contract.pageCount}페이지. 계약내용정리와 향후 일정에는 들어가지 않습니다.",
                color = Brand.TextSub,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onMakeContract, modifier = Modifier.fillMaxWidth()) {
                Text("계약서였다면: 계약서로 바꾸기 (AI 분석)")
            }
        }
    }
}
