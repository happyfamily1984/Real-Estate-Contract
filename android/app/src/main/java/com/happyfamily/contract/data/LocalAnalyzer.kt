package com.happyfamily.contract.data

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * 마지막 대안: 인터넷·AI 없이 휴대폰 안에서 글자를 읽고(구글 ML Kit 한국어 글자 인식, 무료·무제한)
 * [KoreanContractParser] 규칙으로 계약 내용을 뽑는다. AI 보다 정확도는 낮다.
 */
object LocalAnalyzer {
    suspend fun analyze(pages: List<ByteArray>): Map<String, Any?> {
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        try {
            val text = buildString {
                pages.forEachIndexed { i, jpeg ->
                    val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return@forEachIndexed
                    val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
                    bitmap.recycle()
                    append("\n[${i + 1}페이지]\n").append(result.text)
                }
            }
            if (text.isBlank()) error("사진에서 글자를 읽지 못했습니다.")
            return KoreanContractParser.parse(text)
        } finally {
            recognizer.close()
        }
    }
}
