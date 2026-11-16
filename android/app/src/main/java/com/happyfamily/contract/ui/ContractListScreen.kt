package com.happyfamily.contract.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.happyfamily.contract.R
import com.happyfamily.contract.ui.theme.Brand
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.happyfamily.contract.data.AuthRepository
import com.happyfamily.contract.data.Contract
import com.happyfamily.contract.data.ContractRepository
import com.happyfamily.contract.data.ContractStatus
import kotlinx.coroutines.launch

@Composable
fun ContractListScreen(
    userEmail: String,
    onOpen: (String) -> Unit,
    viewModel: ContractListViewModel = viewModel(),
) {
    val activity = LocalContext.current as Activity
    val scope = rememberCoroutineScope()
    val contracts by viewModel.contracts.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val upload by viewModel.upload.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val loadError by viewModel.loadError.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmSignOut by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // 1) 구글 문서 스캐너: 촬영 → 자동 테두리 보정 → 여러 장
    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pages = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                ?.pages?.map { it.imageUri }.orEmpty()
            viewModel.upload(pages, onOpen)
        }
    }
    // 2) 스캐너를 쓸 수 없는 기기용: 갤러리에서 여러 장 선택
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = ContractRepository.MAX_PAGES),
    ) { uris -> viewModel.upload(uris, onOpen) }

    fun pickFromGallery() {
        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun startScan() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(ContractRepository.MAX_PAGES)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener { sender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener {
                scope.launch { snackbar.showSnackbar("스캐너를 열 수 없어 갤러리를 엽니다.") }
                pickFromGallery()
            }
    }

    ContractListContent(
        contracts = contracts,
        query = query,
        onQueryChange = { viewModel.query.value = it },
        upload = upload,
        loadError = loadError,
        snackbar = snackbar,
        onGallery = ::pickFromGallery,
        onScan = ::startScan,
        onSignOut = { confirmSignOut = true },
        onOpen = onOpen,
    )

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
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text("취소") }
            },
        )
    }
}

/** 화면 모양만 그리는 부분. 상태는 [ContractListScreen] 이 넘겨준다 (스크린샷 테스트에서도 사용). */
@Composable
internal fun ContractListContent(
    contracts: List<Contract>?,
    query: String,
    onQueryChange: (String) -> Unit,
    upload: UploadState?,
    loadError: String?,
    snackbar: SnackbarHostState,
    onGallery: () -> Unit,
    onScan: () -> Unit,
    onSignOut: () -> Unit,
    onOpen: (String) -> Unit,
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
                        .padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "내 계약서",
                        color = Brand.Navy,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onGallery, enabled = upload == null) {
                        Icon(
                            Icons.Outlined.Image,
                            contentDescription = "갤러리에서 추가",
                            tint = Brand.Navy,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Logout,
                            contentDescription = "로그아웃",
                            tint = Brand.Navy,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (upload == null) {
                GradientPillButton(
                    text = "계약서 촬영",
                    onClick = onScan,
                    leading = {
                        Icon(
                            Icons.Outlined.CameraAlt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )

            upload?.let { state ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(
                        "사진 저장 중… (${state.done}/${state.total})",
                        color = Brand.Navy,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { if (state.total == 0) 0f else state.done / state.total.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            val list = contracts
            when {
                loadError != null -> CenterText("목록을 불러오지 못했습니다.\n$loadError")
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                list.isEmpty() && query.isBlank() -> EmptyState()
                list.isEmpty() -> CenterText("검색 결과가 없습니다.")
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(list, key = { it.id }) { contract ->
                        ContractCard(contract, onClick = { onOpen(contract.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterText(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Brand.TextSub, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(28.dp)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = TextStyle(color = Brand.Navy, fontSize = 17.sp),
        cursorBrush = SolidColor(Brand.Blue),
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .shadow(10.dp, shape, ambientColor = Brand.Blue.copy(alpha = 0.25f), spotColor = Brand.Blue.copy(alpha = 0.25f))
            .clip(shape)
            .background(Color.White),
        decorationBox = { inner ->
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = Brand.Navy, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("주소, 이름, 메모로 검색", color = Brand.TextSub, fontSize = 17.sp)
                    }
                    inner()
                }
            }
        },
    )
}

@Composable
private fun EmptyState() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 112.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Image(
            painter = painterResource(R.drawable.empty_contracts),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth(0.82f),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            twoToneTitle("아직 저장된 ", "계약서", "가 없습니다."),
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "아래 '계약서 촬영' 버튼을 눌러\n부동산 계약서를 촬영하고 분석해보세요!",
            color = Brand.TextSub,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        FeatureRow(
            listOf(
                Feature(Icons.Filled.CameraAlt, Brand.Blue, "계약서 촬영", "간단하게 촬영"),
                Feature(Icons.Filled.FactCheck, Brand.Blue, "AI 분석", "핵심 정보 추출"),
                Feature(Icons.Filled.Folder, Brand.Violet, "안전한 보관", "언제든지 확인"),
            ),
        )
    }
}

@Composable
private fun ContractCard(contract: Contract, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(contract.thumbnail)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (contract.contractType.isNotBlank()) {
                        AssistChip(onClick = onClick, label = { Text(contract.contractType) })
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        formatDate(contract.createdAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    contract.title.ifBlank { "제목 없음" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                when (contract.status) {
                    ContractStatus.PENDING, ContractStatus.PROCESSING -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("AI가 계약서를 분석하고 있어요", style = MaterialTheme.typography.bodySmall)
                    }
                    ContractStatus.ERROR -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "분석 실패 - 눌러서 다시 시도",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    ContractStatus.DONE -> contract.analysis?.let { a ->
                        Text(
                            keyAmountText(a),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(jpeg: ByteArray?) {
    Box(
        Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        jpeg?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

fun keyAmountText(a: com.happyfamily.contract.data.ContractAnalysis): String = when {
    a.isSale -> "매매 ${formatWon(a.salePrice)}"
    a.monthlyRent > 0 -> "보증금 ${formatWon(a.deposit)} / 월 ${formatWon(a.monthlyRent)}"
    else -> "보증금 ${formatWon(a.deposit)}"
}
