package com.happyfamily.contract.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

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
                    if (contract != null &&
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

            PhotoStrip(contract, onOpenPhoto)

            FolderRow(
                folder = folders.firstOrNull { it.id == contract.folderId },
                analyzing = contract.status != ContractStatus.DONE,
                onOpen = { contract.folderId?.let(onOpenFolder) },
                onMove = { moving = true },
            )

            when (contract.status) {
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

            MemoSection(contract)
        }
    }

    if (moving && contract != null) {
        FolderPickerDialog(
            title = "폴더 옮기기",
            folders = folders,
            currentFolderId = contract.folderId,
            suggestedNewName = contract.analysis?.folderName.orEmpty(),
            onDismiss = { moving = false },
            onPick = { target, newName ->
                moving = false
                if (target != null && target == contract.folderId) return@FolderPickerDialog
                scope.launch {
                    runCatching { FolderRepository.moveContracts(context, listOf(contract), target, newName) }
                        .onSuccess { (_, name) -> actionError = null; moveMessage = "'$name' 폴더로 옮겼어요." }
                        .onFailure { actionError = "옮기지 못했습니다: ${friendlyError(it)}" }
                }
            },
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
private fun FolderRow(folder: Folder?, analyzing: Boolean, onOpen: () -> Unit, onMove: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Folder, contentDescription = null, tint = Brand.Blue)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("계약 폴더", color = Brand.TextSub, fontSize = 12.sp)
                Text(
                    folder?.name ?: if (analyzing) "분석이 끝나면 자동으로 정해져요" else "폴더 없음",
                    color = Brand.Navy,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (folder != null) TextButton(onClick = onOpen) { Text("열기") }
            TextButton(onClick = onMove, enabled = !analyzing) { Text("옮기기") }
        }
    }
}

@Composable
private fun PhotoStrip(contract: Contract, onOpenPhoto: (String, Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(contract.pageCount) { index ->
            val jpeg by produceState<ByteArray?>(null, contract.id, index) {
                value = runCatching { ContractRepository.pageImage(contract.id, index) }.getOrNull()
            }
            Box(
                Modifier
                    .size(width = 96.dp, height = 128.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onOpenPhoto(contract.id, index) },
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
