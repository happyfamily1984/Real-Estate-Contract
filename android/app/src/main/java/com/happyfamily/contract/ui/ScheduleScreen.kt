package com.happyfamily.contract.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.happyfamily.contract.data.ContractRepository
import com.happyfamily.contract.data.Folder
import com.happyfamily.contract.data.FolderRepository
import com.happyfamily.contract.data.Plan
import com.happyfamily.contract.data.ReminderSettings
import com.happyfamily.contract.data.ReminderWorker
import com.happyfamily.contract.data.ScheduleEvent
import com.happyfamily.contract.data.ScheduleRepository
import com.happyfamily.contract.data.formatShortDate
import com.happyfamily.contract.ui.theme.Brand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ScheduleItem(val event: ScheduleEvent, val plan: Plan?)

/** 향후 일정 화면 내용: 날짜 있는 일정 + 다음 일정이 없는 진행중 폴더 */
data class ScheduleState(val items: List<ScheduleItem>, val undated: List<Folder>)

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {
    /** null 이면 불러오는 중 */
    val state: StateFlow<ScheduleState?> = combine(
        FolderRepository.foldersFlow().catch { emit(emptyList()) },
        ContractRepository.contractsFlow().catch { emit(emptyList()) },
        ScheduleRepository.plansFlow().catch { emit(emptyList()) },
        ScheduleRepository.editsFlow().catch { emit(emptyList()) },
    ) { folders, contracts, plans, edits ->
        val byKey = plans.associateBy { it.eventKey }
        val events = ScheduleRepository.applyEdits(ScheduleRepository.events(folders, contracts), edits)
        ScheduleState(
            items = events.map { ScheduleItem(it, byKey[it.key]) },
            undated = ScheduleRepository.foldersWithoutEvents(folders, events),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private fun run(done: String? = null, block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
            done?.let { _message.value = it }
        } catch (e: Exception) {
            _message.value = friendlyError(e)
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun save(plan: Plan) = run { ScheduleRepository.savePlan(plan) }

    fun delete(plan: Plan) = run { ScheduleRepository.deletePlan(plan.id) }

    fun saveEvent(e: ScheduleEvent, type: String, date: String, detail: String, folderName: String) =
        run("일정을 고쳤어요.") { ScheduleRepository.saveEvent(e, type, date, detail, folderName) }

    fun deleteEvent(e: ScheduleEvent) = run("일정을 지웠어요. '계약서 내용으로 다시 적용'을 누르면 되살아납니다.") {
        ScheduleRepository.deleteEvent(e)
    }

    fun addEvent(folderId: String?, folderName: String, type: String, date: String, detail: String) =
        run("일정을 추가했어요.") { ScheduleRepository.addEvent(folderId, folderName, type, date, detail) }

    fun reset() = run {
        val n = ScheduleRepository.resetEdits()
        _message.value = "계약서 내용으로 다시 적용했어요. (고치거나 지운 기록 ${n}건 되돌림)"
    }

    /** 알림 설정을 바꾸면 바로 한 번 확인한다. */
    fun checkNow() = viewModelScope.launch { runCatching { ReminderWorker.check(getApplication()) } }
}

/** 향후 일정: 진행중 계약의 만기·잔금일 등, 알림 설정, 계획 메모 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleScreen(onBack: () -> Unit, onOpenFolder: (String) -> Unit, vm: ScheduleViewModel = viewModel()) {
    val context = LocalContext.current
    val scheduleState by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ScheduleItem?>(null) }
    var editingEvent by remember { mutableStateOf<ScheduleEvent?>(null) }
    /** null 이면 닫힘. 폴더를 정해 추가할 때는 그 폴더 */
    var adding by remember { mutableStateOf<Pair<String?, String>?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }
    var settingsVersion by remember { mutableStateOf(0) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.checkNow() }
    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.checkNow()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.background(Brand.ScreenGradient),
        topBar = {
            TopAppBar(
                title = { Text("향후 일정", fontWeight = FontWeight.Bold) },
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
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("미리 알림 받기", color = Brand.Blue, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("만기·잔금일 등이 다가오면 휴대폰 알림을 보냅니다.", color = Brand.TextSub, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReminderSettings.OPTIONS.forEach { (months, label) ->
                                val on = remember(settingsVersion) { ReminderSettings.enabled(context, months) }
                                FilterChip(
                                    selected = on,
                                    onClick = {
                                        ReminderSettings.set(context, months, !on)
                                        settingsVersion++
                                        if (!on) ensureNotificationPermission()
                                    },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { adding = null to "" }) { Text("+ 일정 추가") }
                    OutlinedButton(onClick = { confirmReset = true }) { Text("계약서 내용으로 다시 적용") }
                }
            }
            val st = scheduleState
            when {
                st == null -> item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                else -> {
                    if (st.items.isEmpty()) item { CenterText("날짜가 정해진 다가오는 일정이 없습니다.") }
                    items(st.items, key = { it.event.key }) { item ->
                        EventCard(
                            item,
                            onOpenFolder = { item.event.folderId?.let(onOpenFolder) },
                            onPlan = { editing = item },
                            onEdit = { editingEvent = item.event },
                        )
                    }
                    if (st.undated.isNotEmpty()) {
                        item {
                            Column(Modifier.padding(top = 12.dp)) {
                                Text("다음 일정이 없는 진행중 계약 ${st.undated.size}", color = Brand.Navy, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "계약서에서 만기·잔금일 등 날짜를 찾지 못했어요. 보유 중이거나 날짜가 빠진 계약입니다. 다음 단계를 일정으로 정해 두세요.",
                                    color = Brand.TextSub,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                )
                            }
                        }
                        items(st.undated, key = { "u-" + it.id }) { f ->
                            Card(
                                Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                            ) {
                                Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f).clickable { onOpenFolder(f.id) }) {
                                        Text(f.name, color = Brand.Blue, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                        val last = f.summaryEntries.lastOrNull { it.ongoing } ?: f.summaryEntries.lastOrNull()
                                        last?.let {
                                            Text(
                                                highlightSummary(com.happyfamily.contract.data.SummaryText.line(it)),
                                                color = Brand.TextSub,
                                                fontSize = 12.sp,
                                                maxLines = 2,
                                            )
                                        }
                                    }
                                    TextButton(onClick = { adding = f.id to f.name }) { Text("+ 일정") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingEvent?.let { e ->
        EventEditDialog(
            title = if (e.custom) "일정 고치기" else "일정 고치기 (계약서에서 뽑은 일정)",
            initialType = e.type,
            initialDate = e.date,
            initialDetail = e.detail,
            initialFolderName = e.folderName,
            folderEditable = e.custom,
            onDismiss = { editingEvent = null },
            onSave = { type, date, detail, folder -> vm.saveEvent(e, type, date, detail, folder); editingEvent = null },
            onDelete = { vm.deleteEvent(e); editingEvent = null },
        )
    }
    adding?.let { (folderId, folderName) ->
        EventEditDialog(
            title = if (folderName.isBlank()) "일정 추가" else "$folderName 일정 추가",
            initialType = "",
            initialDate = java.time.LocalDate.now().plusMonths(1).toString(),
            initialDetail = "",
            initialFolderName = folderName,
            folderEditable = folderId == null,
            onDismiss = { adding = null },
            onSave = { type, date, detail, folder -> vm.addEvent(folderId, folder, type, date, detail); adding = null },
            onDelete = null,
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("계약서 내용으로 다시 적용") },
            text = { Text("고치거나 지운 일정을 모두 되돌리고, 계약서에서 뽑은 일정으로 다시 보여 줍니다. 직접 추가한 일정과 계획 메모는 그대로 둡니다.") },
            confirmButton = { TextButton(onClick = { confirmReset = false; vm.reset() }) { Text("다시 적용") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("취소") } },
        )
    }
    editing?.let { item ->
        PlanDialog(
            item = item,
            onDismiss = { editing = null },
            onSave = { vm.save(it); editing = null },
            onDelete = { vm.delete(it); editing = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventCard(item: ScheduleItem, onOpenFolder: () -> Unit, onPlan: () -> Unit, onEdit: () -> Unit) {
    val e = item.event
    val days = e.daysLeft
    val urgent = days <= 92
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (days == 0L) "D-DAY" else "D-$days",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (urgent) MaterialTheme.colorScheme.error else Brand.Blue)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${formatShortDate(e.date)} ${e.type}",
                    color = Brand.Navy,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                when {
                    e.custom -> Badge("직접 추가", Brand.TextSub)
                    e.edited -> Badge("수정함", Brand.TextSub)
                    e.estimated -> Badge("예상", Brand.Violet)
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = "일정 고치기·지우기", tint = Brand.TextSub, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                e.folderName,
                color = Brand.Blue,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onOpenFolder),
            )
            Text(e.detail, color = Brand.TextSub, fontSize = 13.sp, lineHeight = 18.sp)

            val plan = item.plan
            if (plan != null && (plan.tags.isNotEmpty() || plan.memo.isNotBlank())) {
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brand.CardTint)
                        .padding(10.dp),
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        plan.tags.forEach { Badge(it, Brand.Violet) }
                        if (plan.done) Badge("완료", Brand.TextSub, filled = true)
                    }
                    if (plan.memo.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(plan.memo, color = Brand.Navy, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            }
            TextButton(onClick = onPlan) {
                Icon(Icons.Outlined.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (plan == null) "계획 메모 쓰기" else "계획 메모 고치기")
            }
        }
    }
}

/** 계획 메모: 할 일 꼬리표(재계약 제안, 월세 인상 등) + 자유 메모 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanDialog(item: ScheduleItem, onDismiss: () -> Unit, onSave: (Plan) -> Unit, onDelete: (Plan) -> Unit) {
    val existing = item.plan
    var tags by remember { mutableStateOf(existing?.tags.orEmpty().toSet()) }
    var memo by remember { mutableStateOf(existing?.memo.orEmpty()) }
    var done by remember { mutableStateOf(existing?.done ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${item.event.folderName} · ${item.event.type}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("${formatShortDate(item.event.date)} (D-${item.event.daysLeft})", color = Brand.TextSub, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Plan.TAGS.forEach { tag ->
                        FilterChip(
                            selected = tag in tags,
                            onClick = { tags = if (tag in tags) tags - tag else tags + tag },
                            label = { Text(tag, fontSize = 13.sp) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("계획 메모") },
                    placeholder = { Text("예: 3개월 전 동일조건 재계약 문자, 월세 5% 인상 제안") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.clickable { done = !done }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = done, onCheckedChange = { done = it })
                    Text("처리 완료")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    Plan(
                        id = existing?.id.orEmpty(),
                        eventKey = item.event.key,
                        folderId = item.event.folderId,
                        tags = Plan.TAGS.filter { it in tags },
                        memo = memo.trim(),
                        done = done,
                    ),
                )
            }) { Text("저장") }
        },
        dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = { onDelete(existing) }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
    )
}

/** 일정 고치기·추가: 종류, 날짜(달력), 내용, (직접 추가한 일정이면) 이름 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EventEditDialog(
    title: String,
    initialType: String,
    initialDate: String,
    initialDetail: String,
    initialFolderName: String,
    folderEditable: Boolean,
    onDismiss: () -> Unit,
    onSave: (type: String, date: String, detail: String, folderName: String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var type by remember { mutableStateOf(initialType) }
    var date by remember { mutableStateOf(initialDate) }
    var detail by remember { mutableStateOf(initialDetail) }
    var folderName by remember { mutableStateOf(initialFolderName) }
    var picking by remember { mutableStateOf(false) }
    val validDate = runCatching { java.time.LocalDate.parse(date) }.isSuccess

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (folderEditable) {
                    OutlinedTextField(
                        value = folderName, onValueChange = { folderName = it.take(30) }, singleLine = true,
                        label = { Text("이름 (예: 과천 미소가)") }, modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                } else {
                    Text(initialFolderName, color = Brand.Blue, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                }
                OutlinedTextField(
                    value = type, onValueChange = { type = it.take(20) }, singleLine = true,
                    label = { Text("종류") }, modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("임대차 만기", "잔금일", "중도금", "재계약 협의", "월세 인상", "기타").forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t, fontSize = 12.sp) })
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = date, onValueChange = { date = it.take(10) }, singleLine = true,
                        label = { Text("날짜 (YYYY-MM-DD)") }, isError = !validDate, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { picking = true }) { Text("달력") }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = detail, onValueChange = { detail = it }, label = { Text("내용") },
                    minLines = 2, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(type.trim(), date.trim(), detail.trim(), folderName.trim()) }, enabled = validDate && type.isNotBlank()) {
                Text("저장")
            }
        },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text("삭제", color = MaterialTheme.colorScheme.error) } }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
    )

    if (picking) {
        val initialMillis = runCatching {
            java.time.LocalDate.parse(date).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
                    }
                    picking = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("취소") } },
        ) { DatePicker(state = state) }
    }
}
