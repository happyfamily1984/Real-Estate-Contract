package com.happyfamily.contract.data

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * Firebase AI Logic(Gemini Developer API, 무료 등급)로 계약서 사진을 분석한다.
 * API 키는 앱에 들어 있지 않고 Firebase 프로젝트에 연결되어 있다.
 */
object GeminiAnalyzer {

    /**
     * 위에서부터 차례로 시도한다. 구글이 모델을 은퇴시켜도 다음 이름으로 넘어가도록
     * 항상 최신 Flash 를 가리키는 별칭을 먼저 둔다. 성공한 이름은 기억해 둔다.
     */
    private val MODEL_CANDIDATES = listOf("gemini-flash-latest", "gemini-2.5-flash")

    private const val PREFS = "gemini"
    private const val KEY_MODEL = "working_model"

    private val SYSTEM_PROMPT = """
        당신은 한국 부동산 계약서(매매·전세·월세 등)를 읽고 정리하는 전문가입니다.
        사용자가 촬영한 계약서 사진(여러 페이지일 수 있음)을 보고 계약 내용을 정확히 추출합니다.

        규칙:
        - 금액은 모두 원 단위 정수로 변환합니다. (예: "금 삼억원정", "3억", "300,000,000" → 300000000)
        - 한글 금액과 숫자 금액이 함께 있으면 둘을 대조하고, 서로 다르면 cautions 에 그 사실을 적습니다.
        - 날짜는 YYYY-MM-DD 형식으로 씁니다. 연도가 생략됐으면 문맥상 가장 합리적인 연도를 쓰고 cautions 에 추정했다고 적습니다.
        - 사진에서 읽을 수 없거나 계약서에 없는 문자열 항목은 "" 로, 금액은 0 으로 둡니다. 추측해서 채우지 않습니다.
        - 손글씨나 흐릿한 글자처럼 확신이 낮은 값은 cautions 에 "확인 필요"로 표시합니다.
        - 특약사항은 항목별로 나눠 원문에 가깝게 옮깁니다.
        - summary 는 계약 종류, 대상 주소, 핵심 금액, 기간, 잔금일을 담아 3~5문장으로 씁니다.
        - cautions 에는 임차인/매수인 입장에서 확인해야 할 점(누락된 서명·날인, 비어 있는 칸, 불리할 수 있는 특약, 금액 불일치, 잔금일과 입주일 차이 등)을 적습니다.
        - 사진이 부동산 계약서가 아니면 summary 에 그렇게 적고 나머지는 비워 둡니다.
    """.trimIndent()

    private val payment = Schema.obj(
        mapOf(
            "amount" to Schema.long("금액(원 단위 정수)"),
            "date" to Schema.string("지급일 YYYY-MM-DD"),
        ),
    )

    private val party = Schema.obj(
        mapOf(
            "name" to Schema.string("성명 또는 법인명"),
            "phone" to Schema.string("전화번호"),
            "address" to Schema.string("주소"),
        ),
    )

    private val contractSchema = Schema.obj(
        mapOf(
            "contractType" to Schema.enumeration(listOf("매매", "전세", "월세", "기타"), "계약 종류"),
            "propertyType" to Schema.string("부동산 종류 (아파트, 오피스텔, 다세대, 단독주택, 상가, 토지 등)"),
            "propertyAddress" to Schema.string("부동산 소재지 전체 주소"),
            "area" to Schema.string("면적 (예: 전용 84.97㎡)"),
            "contractDate" to Schema.string("계약 체결일 YYYY-MM-DD"),
            "leaseStart" to Schema.string("임대차 시작일 YYYY-MM-DD (매매면 인도일)"),
            "leaseEnd" to Schema.string("임대차 종료일 YYYY-MM-DD"),
            "salePrice" to Schema.long("매매대금(원). 임대차면 0"),
            "deposit" to Schema.long("보증금(원). 매매면 0"),
            "monthlyRent" to Schema.long("월 차임(원). 없으면 0"),
            "managementFee" to Schema.long("월 관리비(원). 없으면 0"),
            "downPayment" to payment,
            "middlePayments" to Schema.array(payment, "중도금 목록"),
            "balancePayment" to payment,
            "lessor" to party,
            "lessee" to party,
            "agent" to Schema.obj(
                mapOf(
                    "officeName" to Schema.string("중개사무소 명칭"),
                    "representative" to Schema.string("대표 공인중개사 성명"),
                    "registrationNumber" to Schema.string("등록번호"),
                    "phone" to Schema.string("전화번호"),
                    "address" to Schema.string("주소"),
                ),
            ),
            "specialTerms" to Schema.array(Schema.string(), "특약사항 각 항목 (원문에 가깝게)"),
            "summary" to Schema.string("계약 핵심 내용을 3~5문장으로 요약"),
            "cautions" to Schema.array(Schema.string(), "계약자가 확인하거나 주의해야 할 점"),
        ),
    )

    private fun model(name: String) = Firebase.ai(backend = GenerativeBackend.googleAI())
        .generativeModel(
            modelName = name,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = contractSchema
                temperature = 0f
            },
            systemInstruction = content { text(SYSTEM_PROMPT) },
        )

    /** 페이지 사진(JPEG)들을 분석해 Firestore 에 그대로 저장할 수 있는 Map 으로 돌려준다. */
    suspend fun analyze(context: Context, pages: List<ByteArray>): Map<String, Any?> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val remembered = prefs.getString(KEY_MODEL, null)
        val candidates = listOfNotNull(remembered) + MODEL_CANDIDATES.filter { it != remembered }

        val request = content {
            pages.forEachIndexed { i, jpeg ->
                text("[${i + 1} / ${pages.size} 페이지]")
                inlineData(jpeg, "image/jpeg")
            }
            text("위 계약서 사진의 내용을 스키마에 맞춰 정리해 주세요.")
        }

        var lastError: Exception? = null
        for (name in candidates) {
            try {
                val text = model(name).generateContent(request).text
                    ?: throw IllegalStateException("AI가 빈 응답을 보냈습니다. 다시 시도해 주세요.")
                prefs.edit().putString(KEY_MODEL, name).apply()
                return JSONObject(text).toMap()
            } catch (e: ServerException) {
                // 모델 이름이 없어졌거나 서버 오류 → 다음 후보로
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("사용할 수 있는 Gemini 모델이 없습니다.")
    }

    fun friendlyMessage(e: Throwable): String = when (e) {
        is QuotaExceededException -> "오늘 무료 AI 사용량을 다 썼습니다. 내일 다시 시도해 주세요."
        is PromptBlockedException -> "AI가 이 사진의 분석을 거절했습니다. 계약서 사진이 맞는지 확인해 주세요."
        is ServerException -> "AI 서버 오류입니다. 잠시 후 다시 시도해 주세요. (${e.message})"
        is FirebaseAIException -> "AI 분석 중 오류가 발생했습니다. (${e.message})"
        is org.json.JSONException -> "AI 분석 결과를 읽지 못했습니다. 다시 시도해 주세요."
        else -> e.message ?: e.toString()
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
