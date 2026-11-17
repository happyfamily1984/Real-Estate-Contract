package com.happyfamily.contract.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.happyfamily.contract.data.ContractRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(contractId: String, initialPage: Int, onBack: () -> Unit) {
    val flow = remember(contractId) { ContractRepository.contractFlow(contractId) }
    val contract by flow.collectAsStateWithLifecycle(initialValue = null)
    val pageCount = contract?.pageCount ?: 0
    val pagerState = rememberPagerState(initialPage = initialPage) { pageCount }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val foldersFlow = remember { com.happyfamily.contract.data.FolderRepository.foldersFlow() }
    val folders by foldersFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var picking by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    /** true: 지금 보는 페이지만, false: 계약서 전체 */
    var pageOnly by remember { mutableStateOf(true) }
    val busy = contract?.status.let {
        it == com.happyfamily.contract.data.ContractStatus.PENDING || it == com.happyfamily.contract.data.ContractStatus.PROCESSING
    }

    // 새로 올린 계약서(폴더 확인 전)를 크게 볼 때: 여기서 바로 폴더에 넣을 수 있다.
    val c = contract
    val suggestion = if (c != null && !c.confirmed) {
        folders.firstOrNull { it.id == (c.folderId ?: c.suggestedFolderId) }?.name
            ?: c.analysis?.let { "새 폴더 '${it.folderName.ifBlank { c.title.take(12) }}'" }
    } else {
        null
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { if (pageCount > 0) Text("${pagerState.currentPage + 1} / $pageCount") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
        bottomBar = {
            if (c != null) {
                androidx.compose.foundation.layout.Column(
                    Modifier.background(Color.Black).navigationBarsPadding().padding(12.dp),
                ) {
                    val current = folders.firstOrNull { it.id == c.folderId }?.name
                    Text(
                        info ?: when {
                            !c.confirmed && suggestion != null -> "옮길 폴더(제안): $suggestion"
                            current != null -> "지금 폴더: $current"
                            c.analysis == null -> "분석이 끝나면 폴더를 제안해요"
                            else -> "아직 폴더에 넣지 않았어요"
                        },
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                    androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        // 이 페이지만 떼어 옮기기 / 계약서 전체 옮기기 (둘 다 폴더를 고른 뒤 '옮기기'로 확정)
                        androidx.compose.material3.Button(
                            onClick = { pageOnly = true; picking = true },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                        ) { Text(if (pageCount > 1) "이 페이지만 옮기기" else "옮기기") }
                        if (pageCount > 1) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = { pageOnly = false; picking = true },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            ) { Text("전체 ${pageCount}장 옮기기", color = Color.White) }
                        }
                    }
                }
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding).background(Color.Black),
        ) { page ->
            ZoomableImage(contractId, page, pageCount)
        }
    }

    if (picking && c != null) {
        val page = pagerState.currentPage
        val single = pageOnly && pageCount > 1
        FolderPickerDialog(
            title = if (single) "${page + 1}페이지를 어느 폴더로 옮길까요?" else "어느 폴더로 옮길까요?",
            folders = folders,
            currentFolderId = if (single) null else c.folderId ?: c.suggestedFolderId,
            suggestedNewName = if (single) "" else c.analysis?.folderName.orEmpty(),
            onDismiss = { picking = false },
            onPick = { target, newName ->
                picking = false
                scope.launch {
                    runCatching {
                        if (single) {
                            com.happyfamily.contract.data.FolderRepository.movePages(context, c, listOf(page), target, newName)
                        } else {
                            com.happyfamily.contract.data.FolderRepository.moveContracts(context, listOf(c), target, newName).second
                        }
                    }.onSuccess { name ->
                        android.widget.Toast.makeText(context, "'$name' 폴더로 옮겼어요.", android.widget.Toast.LENGTH_SHORT).show()
                        onBack()
                    }.onFailure { info = friendlyError(it) }
                }
            },
        )
    }
}

@Composable
private fun ZoomableImage(contractId: String, index: Int, pageCount: Int = 0) {
    val jpeg by produceState<ByteArray?>(null, contractId, index, pageCount) {
        value = runCatching { ContractRepository.pageImage(contractId, index) }.getOrNull()
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + panChange
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (jpeg == null) {
            CircularProgressIndicator()
        } else {
            AsyncImage(
                model = jpeg,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    // 확대 상태에서만 드래그를 사진 이동에 쓰고, 아니면 옆 페이지로 넘긴다.
                    .transformable(transformState, canPan = { scale > 1f })
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
        }
    }
}
