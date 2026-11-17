package com.happyfamily.contract.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.happyfamily.contract.R
import com.happyfamily.contract.data.Contract
import com.happyfamily.contract.data.ContractAnalysis
import com.happyfamily.contract.data.ContractStatus
import com.happyfamily.contract.data.Folder
import com.happyfamily.contract.data.FolderStatus
import com.happyfamily.contract.data.SummaryText
import com.happyfamily.contract.ui.theme.Brand

@Composable
fun CenterText(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Brand.TextSub, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun SearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
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
                        Text(placeholder, color = Brand.TextSub, fontSize = 17.sp, maxLines = 1)
                    }
                    inner()
                }
            }
        },
    )
}

@Composable
fun UploadProgress(state: UploadState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        val title = if (state.total > 1) "계약서 저장 중… (${state.done + 1}/${state.total}건)" else "계약서 저장 중…"
        Text(title, color = Brand.Navy, style = MaterialTheme.typography.bodyMedium)
        if (state.detail.isNotBlank()) {
            Text(state.detail, color = Brand.TextSub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { if (state.total == 0) 0f else state.done / state.total.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier
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
            "아래 '계약서 촬영'으로 찍거나\n'파일 업로드'로 휴대폰의 PDF·JPG를 올려 보세요!",
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
fun Thumbnail(jpeg: ByteArray?, size: Dp = 64.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(Brand.CardTint),
        contentAlignment = Alignment.Center,
    ) {
        if (jpeg != null) {
            AsyncImage(
                model = jpeg,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Filled.Folder, contentDescription = null, tint = Brand.Divider, modifier = Modifier.size(size / 2))
        }
    }
}

/** 작은 둥근 꼬리표 (진행중, 완료, 계약 종류 등) */
@Composable
fun Badge(text: String, color: Color, filled: Boolean = false) {
    Text(
        text,
        color = if (filled) Color.White else color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) color else color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
fun StatusBadge(status: FolderStatus) =
    if (status == FolderStatus.ONGOING) Badge("진행중", Brand.Blue, filled = true) else Badge("완료", Brand.TextSub)

fun keyAmountText(a: ContractAnalysis): String = when (a.contractType) {
    "매매" -> "매매 ${formatWon(a.salePrice)}"
    "분양" -> "분양 ${formatWon(if (a.acquisitionAmount > 0) a.acquisitionAmount else a.salePrice)}"
    "경매" -> "낙찰 취득 ${formatWon(if (a.acquisitionAmount > 0) a.acquisitionAmount else a.salePrice)}"
    else -> if (a.monthlyRent > 0) {
        "보증금 ${formatWon(a.deposit)} / 월 ${formatWon(a.monthlyRent)}"
    } else {
        "보증금 ${formatWon(a.deposit)}"
    }
}

/** 부동산(계약 폴더) 카드 */
@Composable
fun FolderCard(folder: Folder, contractCount: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(folder.thumbnail, size = 68.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        folder.name,
                        color = Brand.Navy,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusBadge(folder.status)
                }
                if (folder.address.isNotBlank()) {
                    Text(folder.address, color = Brand.TextSub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(4.dp))
                val latest = folder.summaryEntries.lastOrNull { it.ongoing } ?: folder.summaryEntries.lastOrNull()
                if (latest != null) {
                    Text(
                        highlightSummary(SummaryText.line(latest)),
                        color = Brand.Navy,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                val meta = listOfNotNull(
                    "계약서 ${contractCount}건",
                    folder.stakeholder.takeIf { it.isNotBlank() }?.let { "이해관계자 $it" },
                )
                Text(meta.joinToString(" · "), color = Brand.TextSub, fontSize = 12.sp)
            }
        }
    }
}

/** 계약서 한 건 카드. [ongoing] 이면 "현재 진행중" 표시. */
@Composable
fun ContractCard(contract: Contract, onClick: () -> Unit, ongoing: Boolean = false) {
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
                val a = contract.analysis
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (contract.contractType.isNotBlank()) {
                        Badge(SummaryText.kindLabel(contract.contractType), Brand.Violet)
                        Spacer(Modifier.width(8.dp))
                    }
                    val date = a?.representativeDate?.takeIf { it.isNotBlank() }
                    Text(
                        date?.let { formatDate(it) } ?: formatDate(contract.createdAt),
                        color = Brand.TextSub,
                        fontSize = 13.sp,
                    )
                    if (a?.representativeDateEstimated == true) {
                        Spacer(Modifier.width(4.dp))
                        Text("예상", color = Brand.TextSub, fontSize = 10.sp)
                    }
                    if (ongoing) {
                        Spacer(Modifier.width(8.dp))
                        Badge(SummaryText.ONGOING_MARK, Brand.Blue)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    contract.title.ifBlank { "제목 없음" },
                    color = Brand.Navy,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                when (contract.status) {
                    ContractStatus.PENDING -> Text(
                        contract.error ?: "분석 대기 중 · 올린 순서대로 분석해요",
                        color = Brand.TextSub,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ContractStatus.PROCESSING -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("AI가 계약서를 분석하고 있어요", color = Brand.TextSub, style = MaterialTheme.typography.bodySmall)
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
                    ContractStatus.DONE -> a?.let {
                        Text(keyAmountText(it), color = Brand.Blue, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
