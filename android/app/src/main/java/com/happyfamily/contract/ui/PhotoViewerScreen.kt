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
                            !c.confirmed && suggestion != null -> "제안 폴더: $suggestion"
                            current != null -> "지금 폴더: $current"
                            c.analysis == null -> "분석이 끝나면 폴더를 제안해요"
                            else -> "아직 폴더에 넣지 않았어요"
                        },
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                    androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        if (!c.confirmed) {
                            androidx.compose.material3.Button(
                                onClick = {
                                    scope.launch {
                                        runCatching { com.happyfamily.contract.data.FolderRepository.confirmSuggested(context, listOf(c)) }
                                            .onSuccess { onBack() }
                                            .onFailure { info = friendlyError(it) }
                                    }
                                },
                                enabled = suggestion != null,
                                modifier = Modifier.weight(1f),
                            ) { Text("제안 폴더로 넣기") }
                        }
                        androidx.compose.material3.OutlinedButton(onClick = { picking = true }, modifier = Modifier.weight(1f)) {
                            Text(if (c.confirmed) "다른 폴더로 옮기기" else "다른 폴더로", color = Color.White)
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
            ZoomableImage(contractId, page)
        }
    }

    if (picking && c != null) {
        FolderPickerDialog(
            title = "어느 폴더로 옮길까요?",
            folders = folders,
            currentFolderId = c.folderId ?: c.suggestedFolderId,
            suggestedNewName = c.analysis?.folderName.orEmpty(),
            onDismiss = { picking = false },
            onPick = { target, newName ->
                picking = false
                scope.launch {
                    runCatching { com.happyfamily.contract.data.FolderRepository.moveContracts(context, listOf(c), target, newName) }
                        .onSuccess { onBack() }
                        .onFailure { info = friendlyError(it) }
                }
            },
        )
    }
}

@Composable
private fun ZoomableImage(contractId: String, index: Int) {
    val jpeg by produceState<ByteArray?>(null, contractId, index) {
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
