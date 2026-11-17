package com.happyfamily.contract.data.ai

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InvalidAPIKeyException
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.ServiceDisabledException
import com.google.firebase.ai.type.UnsupportedUserLocationException
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.ZoneId
import java.time.ZonedDateTime

/** AI 에 보낼 내용 조각 */
sealed interface AiPart {
    data class Text(val text: String) : AiPart
    class Jpeg(val bytes: ByteArray) : AiPart
}

/** AI 호출 실패를 종류별로 나눈 것 */
class AiFailure(val kind: Kind, message: String) : Exception(message) {
    enum class Kind {
        /** 사용량 한도 (429) */
        QUOTA,
        /** 모델 이름이 없음 (404) */
        NOT_FOUND,
        /** 서버 과부하 (503) — 잠시 뒤 같은 모델로 다시 */
        OVERLOADED,
        SERVER,
        /** 키·설정 문제, 지역 제한 */
        AUTH,
        /** 안전 정책으로 거절 */
        BLOCKED,
        BAD_RESPONSE,
        NETWORK,
    }
}

/** 모든 AI 경로가 지금은 안 될 때. 이때는 기기 안 분석으로 넘어간다. */
class AllAiUnavailableException(val reasons: List<String>) :
    Exception("지금은 쓸 수 있는 AI 가 없습니다.\n" + reasons.joinToString("\n"))

/**
 * AI 호출 관문. 아래 순서로 차례로 시도하고, 한도에 걸린 경로는 잠시(또는 다음 초기화 때까지) 건너뛴다.
 *  1. Firebase AI Logic (Gemini Developer API 무료 등급) — 모델별로 한도가 따로라 여러 모델을 돌아가며
 *  2. 사용자가 넣은 Gemini API 키 (Google AI Studio 무료 키, 여러 개 가능) — 키마다 한도가 따로
 * 모두 안 되면 [AllAiUnavailableException] → 호출한 쪽이 기기 안 분석으로 대신한다.
 */
object AiGateway {
    /** 분석·키 확인에 쓰는 모델 (앞에서부터 차례로). AI 설정 화면에서 바꿀 수 있다([ModelStore]). */
    val DEFAULT_MODELS = listOf("gemini-3.8-flash", "gemini-3.1-flash-lite")

    fun models(context: Context): List<String> = ModelStore.models(context)

    private const val TIMEOUT_MS = 4 * 60_000L

    /** 503(과부하)일 때 같은 모델로 다시 시도하기 전 기다리는 시간 */
    private val OVERLOAD_RETRY_DELAYS_MS = listOf(3_000L, 8_000L, 20_000L)

    class Route(
        val id: String,
        val label: String,
        val model: String,
        val call: suspend (schema: AiSchema, system: String, parts: List<AiPart>) -> String,
    )

    fun routes(context: Context): List<Route> {
        val models = models(context)
        val firebase = models.map { model ->
            Route("firebase:$model", "Firebase Gemini · $model", model) { schema, system, parts ->
                callFirebase(model, schema, system, parts)
            }
        }
        val keys = ApiKeyStore.keys(context).flatMapIndexed { i, key ->
            models.map { model ->
                Route("key:${key.takeLast(6)}:$model", "내 API 키 ${i + 1} (${ApiKeyStore.mask(key)}) · $model", model) { schema, system, parts ->
                    GeminiRest.generate(key, model, schema, system, parts, TIMEOUT_MS)
                }
            }
        }
        return firebase + keys
    }

    /** 경로를 차례로 시도해 JSON 응답을 받는다. */
    suspend fun generateJson(
        context: Context,
        schema: AiSchema,
        system: String,
        parts: List<AiPart>,
    ): Map<String, Any?> {
        val reasons = mutableListOf<String>()
        for (route in routes(context)) {
            val until = Cooldowns.until(context, route.id)
            if (until > System.currentTimeMillis()) {
                reasons += "${route.label}: ${Cooldowns.describe(context, route.id)}"
                continue
            }
            try {
                val text = withOverloadRetry { route.call(schema, system, parts) }
                val json = try {
                    JSONObject(text)
                } catch (e: JSONException) {
                    throw AiFailure(AiFailure.Kind.BAD_RESPONSE, "AI 응답을 읽지 못했습니다.")
                }
                Cooldowns.success(context, route.id)
                return json.toMap()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val f = classify(e)
                Cooldowns.failure(context, route.id, f.kind)
                reasons += "${route.label}: ${f.message}"
            }
        }
        throw AllAiUnavailableException(reasons)
    }

    private suspend fun <T> withOverloadRetry(block: suspend () -> T): T {
        for (wait in OVERLOAD_RETRY_DELAYS_MS) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (classify(e).kind != AiFailure.Kind.OVERLOADED) throw e
                delay(wait)
            }
        }
        return block()
    }

    private suspend fun callFirebase(model: String, schema: AiSchema, system: String, parts: List<AiPart>): String {
        val generative = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = model,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema.toFirebase()
                temperature = 0f
            },
            systemInstruction = content { text(system) },
            requestOptions = RequestOptions(timeoutInMillis = TIMEOUT_MS),
        )
        val request = content {
            parts.forEach { p ->
                when (p) {
                    is AiPart.Text -> text(p.text)
                    is AiPart.Jpeg -> inlineData(p.bytes, "image/jpeg")
                }
            }
        }
        return generative.generateContent(request).text
            ?: throw AiFailure(AiFailure.Kind.BAD_RESPONSE, "AI 가 빈 응답을 보냈습니다.")
    }

    /** 오류 메시지·종류를 보고 어떻게 다룰지 정한다. */
    fun classify(e: Throwable): AiFailure {
        if (e is AiFailure) return e
        val msg = e.message.orEmpty()
        val lower = msg.lowercase()
        fun byMessage(default: AiFailure.Kind): AiFailure.Kind = when {
            "404" in lower || "not found" in lower || "not_found" in lower -> AiFailure.Kind.NOT_FOUND
            "503" in lower || "overloaded" in lower || "unavailable" in lower -> AiFailure.Kind.OVERLOADED
            "429" in lower || "resource_exhausted" in lower || "quota" in lower -> AiFailure.Kind.QUOTA
            else -> default
        }
        return when (e) {
            is QuotaExceededException -> AiFailure(AiFailure.Kind.QUOTA, "무료 사용량 한도에 걸렸습니다.")
            is ServiceDisabledException -> AiFailure(AiFailure.Kind.AUTH, "Firebase 콘솔 → AI Logic 에서 Gemini Developer API 를 켜야 합니다.")
            is InvalidAPIKeyException -> AiFailure(AiFailure.Kind.AUTH, "API 키가 올바르지 않습니다.")
            is UnsupportedUserLocationException -> AiFailure(AiFailure.Kind.AUTH, "이 지역에서는 쓸 수 없습니다.")
            is PromptBlockedException, is ResponseStoppedException -> AiFailure(AiFailure.Kind.BLOCKED, "AI 가 이 서류의 처리를 거절했습니다.")
            is RequestTimeoutException -> AiFailure(AiFailure.Kind.NETWORK, "AI 응답 시간이 초과됐습니다.")
            is ServerException -> byMessage(AiFailure.Kind.SERVER).let { AiFailure(it, describe(it, msg)) }
            is FirebaseAIException -> byMessage(AiFailure.Kind.SERVER).let { AiFailure(it, describe(it, msg)) }
            is java.io.IOException -> AiFailure(AiFailure.Kind.NETWORK, "인터넷 연결 문제: $msg")
            else -> byMessage(AiFailure.Kind.SERVER).let { AiFailure(it, describe(it, msg)) }
        }
    }

    private fun describe(kind: AiFailure.Kind, raw: String) = when (kind) {
        AiFailure.Kind.NOT_FOUND -> "이 모델은 지금 쓸 수 없습니다 (404)."
        AiFailure.Kind.OVERLOADED -> "AI 서버가 붐빕니다 (503)."
        AiFailure.Kind.QUOTA -> "무료 사용량 한도에 걸렸습니다."
        else -> "AI 서버 오류: ${raw.take(120)}"
    }

    private fun JSONObject.toMap(): Map<String, Any?> =
        keys().asSequence().associateWith { key -> convert(get(key)) }

    private fun JSONArray.toList(): List<Any?> = (0 until length()).map { convert(get(it)) }

    private fun convert(value: Any?): Any? = when (value) {
        is JSONObject -> value.toMap()
        is JSONArray -> value.toList()
        is Int -> value.toLong()
        JSONObject.NULL -> null
        else -> value
    }
}

/**
 * 경로별 "잠시 쉬기" 기록. 한도에 걸린 모델을 계속 두드리지 않게 한다.
 *  - 한도(429): 처음엔 2분, 30분 안에 또 걸리면 하루 한도로 보고 구글 무료 한도 초기화 시각(미국 태평양 자정)까지
 *  - 404: 7일 (모델이 없어진 것)
 *  - 키·설정 문제: 1시간
 *  - 503·서버·네트워크: 1분
 */
object Cooldowns {
    private const val PREFS = "ai_cooldowns"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun until(context: Context, id: String): Long = prefs(context).getLong("until:$id", 0L)

    fun describe(context: Context, id: String): String {
        val until = until(context, id)
        if (until <= System.currentTimeMillis()) return "사용 가능"
        val time = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(until), ZoneId.systemDefault())
        val reason = prefs(context).getString("reason:$id", null) ?: "잠시 쉼"
        return "$reason · ${time.monthValue}/${time.dayOfMonth} ${"%02d:%02d".format(time.hour, time.minute)} 이후 다시"
    }

    fun success(context: Context, id: String) {
        prefs(context).edit().remove("until:$id").remove("hits:$id").remove("reason:$id").apply()
    }

    fun failure(context: Context, id: String, kind: AiFailure.Kind) {
        val now = System.currentTimeMillis()
        val p = prefs(context)
        val (wait, reason) = when (kind) {
            AiFailure.Kind.QUOTA -> {
                val lastHit = p.getLong("lastHit:$id", 0L)
                val hits = if (now - lastHit < 30 * 60_000L) p.getInt("hits:$id", 0) + 1 else 1
                p.edit().putInt("hits:$id", hits).putLong("lastHit:$id", now).apply()
                if (hits >= 2) (nextQuotaReset() - now) to "오늘 한도 소진" else 2 * 60_000L to "분당 한도"
            }
            AiFailure.Kind.NOT_FOUND -> 7 * 24 * 3_600_000L to "모델 없음"
            AiFailure.Kind.AUTH -> 3_600_000L to "설정 확인 필요"
            AiFailure.Kind.OVERLOADED, AiFailure.Kind.SERVER, AiFailure.Kind.NETWORK -> 60_000L to "서버 문제"
            AiFailure.Kind.BLOCKED, AiFailure.Kind.BAD_RESPONSE -> 0L to ""
        }
        if (wait > 0) p.edit().putLong("until:$id", now + wait).putString("reason:$id", reason).apply()
    }

    /** 사용자가 "다시 시도"를 원할 때 모든 기록을 지운다. */
    fun clearAll(context: Context) = prefs(context).edit().clear().apply()

    /** 지금 바로 쓸 수 있는 경로가 하나라도 있는지 */
    fun anyAvailable(context: Context): Boolean =
        AiGateway.routes(context).any { until(context, it.id) <= System.currentTimeMillis() }

    /** Gemini 무료 한도는 미국 태평양 시간 자정에 초기화된다. */
    private fun nextQuotaReset(): Long {
        val pacific = ZoneId.of("America/Los_Angeles")
        val next = ZonedDateTime.now(pacific).toLocalDate().plusDays(1).atStartOfDay(pacific)
        return next.toInstant().toEpochMilli() + 5 * 60_000L
    }
}

/** 사용할 모델 이름 목록 (AI 설정에서 바꿀 수 있음). 휴대폰 안에 저장된다. */
object ModelStore {
    private const val PREFS = "ai_models"
    private const val KEY = "models"

    fun models(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
            ?: AiGateway.DEFAULT_MODELS

    /** 쉼표·줄바꿈으로 구분된 이름들. 비우면 기본값으로 돌아간다. */
    fun save(context: Context, text: String) {
        val list = text.split(",", "\n").map { it.trim() }.filter { it.isNotBlank() }.distinct()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (list.isEmpty()) remove(KEY) else putString(KEY, list.joinToString(","))
        }.apply()
    }

    fun isDefault(context: Context) = models(context) == AiGateway.DEFAULT_MODELS
}
