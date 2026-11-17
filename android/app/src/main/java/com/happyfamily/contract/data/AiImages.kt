package com.happyfamily.contract.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * AI 에 보낼 사진을 준비한다. Gemini 요청 하나는 약 20MB 까지라서
 * 페이지가 많으면 글씨가 읽히는 선에서 크기·화질을 낮춘다. 저장된 원본 사진은 그대로 둔다.
 */
object AiImages {
    /** base64 로 늘어나는 것까지 생각한 원본 바이트 합계 상한 */
    const val BUDGET_BYTES = 9_000_000

    data class Prepared(val pages: List<ByteArray>, val skipped: Int)

    fun prepare(pages: List<ByteArray>): Prepared {
        if (pages.sumOf { it.size } <= BUDGET_BYTES) return Prepared(pages, 0)
        for ((edge, quality) in listOf(1600 to 75, 1300 to 65, 1100 to 55)) {
            val shrunk = pages.map { reencode(it, edge, quality) }
            if (shrunk.sumOf { it.size } <= BUDGET_BYTES) return Prepared(shrunk, 0)
        }
        // 그래도 크면 앞 페이지부터 들어가는 만큼만 보낸다.
        val small = pages.map { reencode(it, 1100, 55) }
        var total = 0
        val fit = small.takeWhile { total += it.size; total <= BUDGET_BYTES }
        return Prepared(fit, pages.size - fit.size)
    }

    private fun reencode(jpeg: ByteArray, maxEdge: Int, quality: Int): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return jpeg
        val longest = max(bitmap.width, bitmap.height)
        val scaled = if (longest > maxEdge) {
            val scale = maxEdge.toFloat() / longest
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        return out.toByteArray()
    }
}
