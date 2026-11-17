package com.happyfamily.contract.data.ai

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 사용자가 넣은 Gemini API 키(Google AI Studio 무료 키)로 Gemini 를 직접 부른다.
 * Firebase 무료 한도를 다 썼을 때 쓰는 두 번째 경로. 키마다 한도가 따로라 여러 개 넣으면 그만큼 더 쓸 수 있다.
 */
object GeminiRest {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models"

    suspend fun generate(
        apiKey: String,
        model: String,
        schema: AiSchema,
        system: String,
        parts: List<AiPart>,
        timeoutMs: Long,
    ): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user").put(
                        "parts",
                        JSONArray().apply {
                            parts.forEach { p ->
                                when (p) {
                                    is AiPart.Text -> put(JSONObject().put("text", p.text))
                                    is AiPart.Jpeg -> put(
                                        JSONObject().put(
                                            "inline_data",
                                            JSONObject()
                                                .put("mime_type", "image/jpeg")
                                                .put("data", Base64.encodeToString(p.bytes, Base64.NO_WRAP)),
                                        ),
                                    )
                                }
                            }
                        },
                    ),
                ),
            )
            put(
                "generationConfig",
                JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("responseSchema", schema.toRestJson())
                    .put("temperature", 0),
            )
        }.toString()

        val conn = (URL("$BASE/$model:generateContent").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = timeoutMs.toInt()
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw failureFor(code, text)

            val json = JSONObject(text)
            val candidates = json.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                val block = json.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                throw AiFailure(AiFailure.Kind.BLOCKED, "AI 가 이 서류의 처리를 거절했습니다. $block".trim())
            }
            val partsJson = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                ?: throw AiFailure(AiFailure.Kind.BAD_RESPONSE, "AI 가 빈 응답을 보냈습니다.")
            (0 until partsJson.length()).joinToString("") { partsJson.getJSONObject(it).optString("text") }
                .ifBlank { throw AiFailure(AiFailure.Kind.BAD_RESPONSE, "AI 가 빈 응답을 보냈습니다.") }
        } finally {
            conn.disconnect()
        }
    }

    private fun failureFor(code: Int, body: String): AiFailure {
        val message = runCatching { JSONObject(body).getJSONObject("error").optString("message") }.getOrNull().orEmpty()
        val kind = when {
            code == 429 -> AiFailure.Kind.QUOTA
            code == 404 -> AiFailure.Kind.NOT_FOUND
            code == 503 -> AiFailure.Kind.OVERLOADED
            code == 401 || code == 403 -> AiFailure.Kind.AUTH
            code == 400 && message.contains("API key", ignoreCase = true) -> AiFailure.Kind.AUTH
            code >= 500 -> AiFailure.Kind.SERVER
            else -> AiFailure.Kind.SERVER
        }
        val text = when (kind) {
            AiFailure.Kind.QUOTA -> "무료 사용량 한도에 걸렸습니다 (429)."
            AiFailure.Kind.NOT_FOUND -> "이 모델은 지금 쓸 수 없습니다 (404)."
            AiFailure.Kind.OVERLOADED -> "AI 서버가 붐빕니다 (503)."
            AiFailure.Kind.AUTH -> "API 키가 올바르지 않거나 막혀 있습니다 ($code)."
            else -> "AI 서버 오류 ($code) ${message.take(120)}"
        }
        return AiFailure(kind, text)
    }
}

/**
 * 사용자가 넣은 Gemini API 키 보관. 이 휴대폰 안(앱 전용 저장소)에만 저장되고
 * 클라우드나 GitHub 로는 올라가지 않는다.
 */
object ApiKeyStore {
    private const val PREFS = "ai_keys"
    private const val KEY = "keys"

    fun keys(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]")
        return runCatching { JSONArray(raw).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList())
    }

    fun add(context: Context, key: String) {
        val list = (keys(context) + key.trim()).distinct()
        save(context, list)
    }

    fun remove(context: Context, key: String) = save(context, keys(context) - key)

    private fun save(context: Context, list: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, JSONArray(list).toString()).apply()
    }

    fun mask(key: String) = if (key.length <= 8) "••••" else key.take(4) + "••••" + key.takeLast(4)

    /**
     * 키가 실제로 동작하는지 아주 짧은 요청으로 확인한다.
     * 결과: (저장해도 되는지, 안내 문구). 한도·과부하는 키 자체는 정상이므로 저장해도 된다.
     */
    suspend fun test(key: String): Pair<Boolean, String> {
        val error = runCatching {
            GeminiRest.generate(
                key.trim(), AiGateway.MODELS.last(),
                AiSchema.Obj(mapOf("ok" to AiSchema.Str())),
                "JSON 으로만 답합니다.",
                listOf(AiPart.Text("ok 필드에 \"네\" 라고 넣어 주세요.")),
                timeoutMs = 30_000,
            )
        }.exceptionOrNull() ?: return true to "키가 정상입니다."
        val f = AiGateway.classify(error)
        return when (f.kind) {
            AiFailure.Kind.QUOTA, AiFailure.Kind.OVERLOADED -> true to "키는 정상이지만 지금은 ${f.message}"
            else -> false to (f.message ?: "확인 실패")
        }
    }
}
