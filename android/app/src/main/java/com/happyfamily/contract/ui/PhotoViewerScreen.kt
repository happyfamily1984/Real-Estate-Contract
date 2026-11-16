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
import com.happyfamily.contract.data.ContractRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(contractId: String, initialPage: Int, onBack: () -> Unit) {
    val flow = remember(contractId) { ContractRepository.contractFlow(contractId) }
    val contract by flow.collectAsStateWithLifecycle(initialValue = null)
    val pageCount = contract?.pageCount ?: 0
    val pagerState = rememberPagerState(initialPage = initialPage) { pageCount }

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
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding).background(Color.Black),
        ) { page ->
            ZoomableImage(contractId, page)
        }
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
