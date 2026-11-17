package com.happyfamily.contract.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.happyfamily.contract.data.Folder
import com.happyfamily.contract.ui.theme.Brand

/**
 * 옮길 폴더 고르기. 기존 폴더를 누르면 바로 그 폴더로, 아래에서 이름을 입력하면 새 폴더를 만들어 옮긴다.
 * [onPick] 의 첫 값이 null 이면 새 폴더(두 번째 값이 이름).
 */
@Composable
fun FolderPickerDialog(
    title: String,
    folders: List<Folder>,
    currentFolderId: String?,
    suggestedNewName: String,
    onDismiss: () -> Unit,
    onPick: (folderId: String?, newName: String) -> Unit,
) {
    var newName by remember { mutableStateOf(suggestedNewName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(folders.sortedBy { it.name }, key = { it.id }) { f ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(f.id, "") }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = f.id == currentFolderId, onClick = { onPick(f.id, "") })
                            Column {
                                Text(f.name + if (f.id == currentFolderId) " (지금 폴더)" else "", fontWeight = FontWeight.SemiBold)
                                if (f.address.isNotBlank()) Text(f.address, color = Brand.TextSub, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("새 폴더 만들어 옮기기", color = Brand.Blue, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(30) },
                    singleLine = true,
                    label = { Text("새 폴더 이름") },
                    placeholder = { Text("예: 과천 미소가 101동") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                // 같은 이름 폴더가 이미 있으면 새로 만들지 않고 그 폴더로 (같은 이름 폴더가 두 개 생기지 않게)
                val same = folders.firstOrNull { it.name.trim() == newName.trim() }
                when {
                    same != null && same.id == currentFolderId -> Text(
                        "지금 폴더와 이름이 같아요. 다른 이름을 입력하거나 위에서 폴더를 고르세요.",
                        color = Brand.TextSub,
                        fontSize = 12.sp,
                    )
                    same != null -> Button(onClick = { onPick(same.id, "") }, modifier = Modifier.fillMaxWidth()) {
                        Text("이미 있는 '${same.name}' 폴더로 옮기기")
                    }
                    else -> Button(
                        onClick = { onPick(null, newName.trim()) },
                        enabled = newName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("새 폴더 '${newName.ifBlank { "…" }}' 만들어 옮기기") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}
