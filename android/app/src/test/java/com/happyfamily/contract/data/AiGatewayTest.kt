package com.happyfamily.contract.data

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.happyfamily.contract.data.ai.AiFailure
import com.happyfamily.contract.data.ai.AiGateway
import com.happyfamily.contract.data.ai.AiSchema
import com.happyfamily.contract.data.ai.Cooldowns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiGatewayTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `모델은 2_5 flash 와 flash-lite 를 바로 쓴다`() {
        assertEquals(listOf("gemini-2.5-flash", "gemini-2.5-flash-lite"), AiGateway.MODELS)
    }

    @Test
    fun `오류 종류 나누기`() {
        assertEquals(AiFailure.Kind.OVERLOADED, AiGateway.classify(RuntimeException("503 The model is overloaded")).kind)
        assertEquals(AiFailure.Kind.NOT_FOUND, AiGateway.classify(RuntimeException("404 models/x is not found")).kind)
        assertEquals(AiFailure.Kind.QUOTA, AiGateway.classify(RuntimeException("429 RESOURCE_EXHAUSTED")).kind)
        assertEquals(AiFailure.Kind.NETWORK, AiGateway.classify(java.io.IOException("timeout")).kind)
    }

    @Test
    fun `한도에 두 번 걸리면 하루 쉬고 성공하면 풀린다`() {
        Cooldowns.clearAll(context)
        val id = "firebase:gemini-2.5-flash"
        Cooldowns.failure(context, id, AiFailure.Kind.QUOTA)
        val first = Cooldowns.until(context, id) - System.currentTimeMillis()
        assertTrue(first in 60_000..120_000) // 처음엔 2분
        Cooldowns.failure(context, id, AiFailure.Kind.QUOTA)
        assertTrue(Cooldowns.until(context, id) - System.currentTimeMillis() > 10 * 60_000) // 하루 한도로 판단
        assertTrue(Cooldowns.describe(context, id).contains("오늘 한도 소진"))
        Cooldowns.success(context, id)
        assertEquals(0L, Cooldowns.until(context, id))
        // 다른 모델은 그대로 사용 가능
        assertTrue(Cooldowns.anyAvailable(context))
    }

    @Test
    fun `모든 경로가 막히면 사용 가능 경로 없음`() {
        Cooldowns.clearAll(context)
        AiGateway.routes(context).forEach { Cooldowns.failure(context, it.id, AiFailure.Kind.NOT_FOUND) }
        assertFalse(Cooldowns.anyAvailable(context))
        Cooldowns.clearAll(context)
    }

    @Test
    fun `REST 스키마 변환`() {
        val json = AiSchema.Obj(mapOf("amount" to AiSchema.Long("금액"), "kind" to AiSchema.Enum(listOf("매매", "전세")))).toRestJson()
        assertEquals("OBJECT", json.getString("type"))
        assertEquals("INTEGER", json.getJSONObject("properties").getJSONObject("amount").getString("type"))
        assertEquals("전세", json.getJSONObject("properties").getJSONObject("kind").getJSONArray("enum").getString(1))
        assertEquals(2, json.getJSONArray("required").length())
    }
}
