package com.happyfamily.contract.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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

private const val NEW_FOLDER = "\u0000new"

/**
 * 옮길 폴더 고르기. 폴더(또는 '새 폴더')를 고른 뒤 아래 '옮기기'를 눌러야 실제로 옮겨진다.
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
    // 처음 선택: 지금(제안) 폴더가 있으면 그것, 없으면 새 폴더
    var choice by remember { mutableStateOf(currentFolderId ?: NEW_FOLDER) }

    // 새 폴더 이름이 이미 있는 폴더와 같으면 그 폴더로 옮긴다 (같은 이름 폴더가 두 개 생기지 않게).
    val sameName = folders.firstOrNull { it.name.trim() == newName.trim() }
    val canMove = if (choice == NEW_FOLDER) newName.isNotBlank() else folders.any { it.id == choice }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(folders.sortedBy { it.name }, key = { it.id }) { f ->
                        Row(
                            Modifier.fillMaxWidth().clickable { choice = f.id }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = choice == f.id, onClick = { choice = f.id })
                            Column {
                                Text(f.name + if (f.id == currentFolderId) " (지금 폴더)" else "", fontWeight = FontWeight.SemiBold)
                                if (f.address.isNotBlank()) Text(f.address, color = Brand.TextSub, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { choice = NEW_FOLDER },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = choice == NEW_FOLDER, onClick = { choice = NEW_FOLDER })
                    Text("새 폴더 만들어 옮기기", color = Brand.Blue, fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(30); choice = NEW_FOLDER },
                    singleLine = true,
                    label = { Text("새 폴더 이름") },
                    placeholder = { Text("예: 과천 미소가 101동") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (choice == NEW_FOLDER && sameName != null) {
                    Text("같은 이름의 폴더가 이미 있어서 그 폴더로 옮깁니다.", color = Brand.TextSub, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (choice == NEW_FOLDER) {
                        if (sameName != null) onPick(sameName.id, "") else onPick(null, newName.trim())
                    } else {
                        onPick(choice, "")
                    }
                },
                enabled = canMove,
            ) { Text("옮기기") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}
