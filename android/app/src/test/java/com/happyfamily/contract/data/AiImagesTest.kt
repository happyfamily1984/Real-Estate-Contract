package com.happyfamily.contract.data

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AiImagesTest {
    /** 압축이 잘 안 되는 잡음 사진 (최악의 경우) */
    private fun noisyJpeg(seed: Int): ByteArray {
        val w = 1500
        val h = 2000
        val rnd = Random(seed)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(IntArray(w * h) { 0xFF000000.toInt() or rnd.nextInt(0xFFFFFF) }, 0, w, 0, 0, w, h)
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }

    @Test
    fun `50페이지도 요청 한도 안으로 줄인다`() {
        val page = noisyJpeg(1)
        val pages = List(50) { page }
        val prepared = AiImages.prepare(pages)
        val total = prepared.pages.sumOf { it.size }
        println("원본 ${pages.sumOf { it.size } / 1_000_000}MB → ${total / 1_000_000}MB, 보낸 페이지 ${prepared.pages.size}, 빠진 페이지 ${prepared.skipped}")
        assertTrue(total <= 13_000_000)
        assertEquals(50, prepared.pages.size + prepared.skipped)
    }

    @Test
    fun `작으면 그대로 보낸다`() {
        val pages = List(3) { ByteArray(200_000) }
        val prepared = AiImages.prepare(pages)
        assertEquals(pages, prepared.pages)
        assertEquals(0, prepared.skipped)
    }
}
