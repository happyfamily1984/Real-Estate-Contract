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

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {
    /** null 이면 불러오는 중 */
    val items: StateFlow<List<ScheduleItem>?> = combine(
        FolderRepository.foldersFlow().catch { emit(emptyList()) },
        ContractRepository.contractsFlow().catch { emit(emptyList()) },
        ScheduleRepository.plansFlow().catch { emit(emptyList()) },
    ) { folders, contracts, plans ->
        val byKey = plans.associateBy { it.eventKey }
        ScheduleRepository.events(folders, contracts).map { ScheduleItem(it, byKey[it.key]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(plan: Plan) = viewModelScope.launch { runCatching { ScheduleRepository.savePlan(plan) } }

    fun delete(plan: Plan) = viewModelScope.launch { runCatching { ScheduleRepository.deletePlan(plan.id) } }

    /** 알림 설정을 바꾸면 바로 한 번 확인한다. */
    fun checkNow() = viewModelScope.launch { runCatching { ReminderWorker.check(getApplication()) } }
}

/** 향후 일정: 진행중 계약의 만기·잔금일 등, 알림 설정, 계획 메모 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleScreen(onBack: () -> Unit, onOpenFolder: (String) -> Unit, vm: ScheduleViewModel = viewModel()) {
    val context = LocalContext.current
    val items by vm.items.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ScheduleItem?>(null) }
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
            val list = items
            when {
                list == null -> item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                list.isEmpty() -> item { CenterText("진행중인 계약의 다가오는 일정이 없습니다.\n(임대차 만기, 잔금일 등)") }
                else -> items(list, key = { it.event.key }) { item ->
                    EventCard(item, onOpenFolder = { item.event.folderId?.let(onOpenFolder) }, onPlan = { editing = item })
                }
            }
        }
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
private fun EventCard(item: ScheduleItem, onOpenFolder: () -> Unit, onPlan: () -> Unit) {
    val e = item.event
    val days = e.daysLeft
    val urgent = days <= 92
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
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
                Text("${formatShortDate(e.date)} ${e.type}", color = Brand.Navy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
