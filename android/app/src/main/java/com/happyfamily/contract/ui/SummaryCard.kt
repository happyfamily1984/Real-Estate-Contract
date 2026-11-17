package com.happyfamily.contract.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.happyfamily.contract.data.SummaryText
import com.happyfamily.contract.ui.theme.Brand

/**
 * "계약내용정리" 카드. 폴더 화면과 이해관계자 문서 화면에서 같이 쓴다.
 * 글을 직접 고칠 수 있고, 적용을 누르면 계약서에서 뽑은 내용으로 다시 정리한다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SummaryCard(
    title: String,
    text: String,
    running: Boolean,
    message: String?,
    edited: Boolean,
    emptyHint: String,
    onSave: (String) -> Unit,
    onApply: () -> Unit,
    onCopy: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(text) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    color = Brand.Blue,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (edited && !editing) {
                    Text("직접 수정함", color = Brand.TextSub, fontSize = 12.sp)
                }
                if (!editing) {
                    TextButton(onClick = { draft = text; editing = true }, enabled = !running) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("수정")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            when {
                editing -> {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { editing = false }) { Text("취소") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { onSave(draft); editing = false }) { Text("저장") }
                    }
                }
                running -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("계약서에서 뽑아 둔 내용으로 정리하고 있어요…", color = Brand.TextSub, fontSize = 14.sp)
                }
                text.isBlank() -> Text(emptyHint, color = Brand.TextSub, fontSize = 14.sp, lineHeight = 21.sp)
                else -> Text(highlightSummary(text), color = Brand.Navy, fontSize = 15.sp, lineHeight = 24.sp)
            }

            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Brand.TextSub, fontSize = 12.sp, lineHeight = 17.sp)
            }

            if (!editing) {
                Spacer(Modifier.height(14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onApply,
                        enabled = !running,
                        colors = ButtonDefaults.buttonColors(containerColor = Brand.Blue),
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("계약서 내용적용")
                    }
                    SmallAction(Icons.Outlined.ContentCopy, "복사", enabled = text.isNotBlank(), onClick = onCopy)
                    SmallAction(Icons.Outlined.IosShare, "내보내기", enabled = text.isNotBlank(), onClick = onExport)
                }
            }
        }
    }
}

@Composable
private fun SmallAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

/** "현재 진행중" 은 파랗고 굵게, "(예상)" 은 작고 흐리게, "[진행중]"·"[완료]" 머리줄은 굵게 */
fun highlightSummary(text: String): AnnotatedString = buildAnnotatedString {
    val marks = Regex("${Regex.escape(SummaryText.ONGOING_MARK)}|${Regex.escape(SummaryText.ESTIMATED_MARK)}")
    text.lines().forEachIndexed { index, line ->
        if (index > 0) append("\n")
        val heading = line.startsWith("[진행중]") || line.startsWith("[완료]")
        if (heading) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = if (line.startsWith("[진행중]")) Brand.Blue else Brand.Navy)) {
                append(line)
            }
            return@forEachIndexed
        }
        var last = 0
        for (m in marks.findAll(line)) {
            append(line.substring(last, m.range.first))
            if (m.value == SummaryText.ONGOING_MARK) {
                withStyle(SpanStyle(color = Brand.Blue, fontWeight = FontWeight.Bold)) { append(m.value) }
            } else {
                withStyle(SpanStyle(color = Brand.TextSub, fontSize = 11.sp)) { append(m.value) }
            }
            last = m.range.last + 1
        }
        append(line.substring(last))
    }
}
