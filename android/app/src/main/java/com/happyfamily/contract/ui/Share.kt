package com.happyfamily.contract.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/** 정리 문서를 클립보드에 복사한다. */
fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "클립보드에 복사했습니다.", Toast.LENGTH_SHORT).show()
}

/** 정리 문서를 텍스트 파일로 만들어 공유 화면을 연다 (파일 저장, 카카오톡, 메일, 드라이브 등). */
fun exportTextFile(context: Context, title: String, text: String) {
    val safe = title.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim('_').ifBlank { "계약내용정리" }
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, "$safe.txt")
    file.writeText(text)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "내보내기"))
}
