package com.happyfamily.contract.data

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
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
 * Firebase AI Logic(Gemini Developer API, 무료 등급)로 계약서를 분석하고 정리한다.
 * API 키는 앱에 들어 있지 않고 Firebase 프로젝트에 연결되어 있다.
 *
 * - [analyze]: 계약서 사진 → 구조화된 내용 (계약서를 올릴 때 한 번만)
 * - [summarize]: 저장해 둔 핵심 텍스트 → 이해관계자 기준 정리 (사진 없이 텍스트만, 빠름)
 */
object GeminiAnalyzer {

    /**
     * 위에서부터 차례로 시도한다. 구글이 모델을 은퇴시켜도 다음 이름으로 넘어가도록
     * 항상 최신 Flash 를 가리키는 별칭을 먼저 둔다. 성공한 이름은 기억해 둔다.
     */
    private val MODEL_CANDIDATES = listOf("gemini-flash-latest", "gemini-2.5-flash")

    private const val PREFS = "gemini"
    private const val KEY_MODEL = "working_model"

    private val CONTRACT_TYPES = listOf("매매", "전세", "월세", "분양", "경매", "기타")

    private val EXTRACT_PROMPT = """
        당신은 한국 부동산 계약서와 관련 서류(매매·전세·월세 계약서, 분양계약서, 경매 낙찰 관련 서류 등)를 읽고 정리하는 전문가입니다.
        사용자가 올린 서류 사진(여러 페이지일 수 있음)을 보고 내용을 정확히 추출합니다.

        공통 규칙:
        - 금액은 모두 원 단위 정수로 변환합니다. (예: "금 삼억원정", "3억", "300,000,000" → 300000000)
        - 한글 금액과 숫자 금액이 함께 있으면 둘을 대조하고, 서로 다르면 cautions 에 그 사실을 적습니다.
        - 날짜는 YYYY-MM-DD 형식으로 씁니다. 연도가 생략됐으면 문맥상 가장 합리적인 연도를 쓰고 cautions 에 추정했다고 적습니다.
        - 사진에서 읽을 수 없거나 서류에 없는 문자열 항목은 "" 로, 금액은 0 으로 둡니다. 추측해서 채우지 않습니다.
        - 손글씨나 흐릿한 글자처럼 확신이 낮은 값은 cautions 에 "확인 필요"로 표시합니다.
        - 특약사항은 항목별로 나눠 원문에 가깝게 옮깁니다.
        - summary 는 계약 종류, 대상 주소, 핵심 금액, 기간, 잔금일을 담아 3~5문장으로 씁니다.
        - cautions 에는 임차인/매수인 입장에서 확인해야 할 점(누락된 서명·날인, 비어 있는 칸, 불리할 수 있는 특약, 금액 불일치, 잔금일과 입주일 차이 등)을 적습니다.

        서류 종류별 규칙 (contractType):
        - 매매: lessor=매도인, lessee=매수인, salePrice=매매대금. acquisitionAmount=매매대금, acquisitionDate=잔금일.
        - 전세·월세: lessor=임대인, lessee=임차인. acquisitionDate="", acquisitionAmount=0.
        - 분양(분양계약서·공급계약서): lessor=분양자(시행사·조합 등), lessee=수분양자.
          분양금액(공급금액 합계)을 salePrice 와 acquisitionAmount 에 넣습니다(매수 취득금액).
          acquisitionDate 는 잔금일·입주지정일 등 식별 가능한 날짜, 없으면 "".
        - 경매(매각허가결정, 대금지급기한 통지서, 매각대금 완납증명, 잔금 납부 영수증, 낙찰 관련 서류 등):
          lessee=낙찰자(매수인), lessor=채무자 또는 소유자, salePrice=매각대금(낙찰가).
          acquisitionDate 는 잔금(매각대금) 납부일을 쓰고, 없으면 매각허가결정일·대금지급기한 등 서류에서 식별 가능한 날짜를 씁니다.
          그 날짜가 실제 취득일인지 확실하지 않으면 acquisitionDateEstimated=true 로 둡니다.
          acquisitionAmount 는 잔금 납부금액입니다. contractDate 는 매각기일 또는 서류 작성일.
        - 그 밖의 서류는 "기타".

        정리용 항목:
        - folderName: 이 부동산을 부를 짧은 이름. 지역(동·구·시 중 짧은 것) + 단지·건물 이름을 줄여 2~10자로. 예: "과천 미소가", "목동 건영", "분당 정자 아이파크".
        - keyFacts: 나중에 다른 계약서와 함께 정리할 때 쓸 핵심 내용을 5~10줄 텍스트로. 서류 종류, 날짜(계약일·잔금일·취득일), 금액, 당사자 이름, 기간, 중요한 특약·조건.
        - matchFolderId: 아래 "기존 폴더" 목록 중 이 서류와 같은 부동산(같은 단지, 같은 동·호수)의 폴더가 있으면 그 id, 없으면 "".
        - 서류가 부동산 관련 서류가 아니면 summary 에 그렇게 적고 나머지는 비워 둡니다.
    """.trimIndent()

    private val SUMMARIZE_PROMPT = """
        당신은 한 사람(이해관계자)의 부동산 계약 이력을 정리하는 전문가입니다.
        폴더별 계약서 핵심 내용을 읽고, 이해관계자 입장에서 계약 하나당 한 줄(entry)로 정리합니다.

        규칙:
        - role: 이해관계자가 그 계약에서 맡은 역할. 매수(매수인·수분양자·낙찰자), 매도(매도인), 임차(임차인), 임대(임대인), 해당 없으면 기타.
          이름은 공동명의, 띄어쓰기, 일부만 적힌 경우도 같은 사람으로 봅니다.
        - 이해관계자가 주어지지 않으면 서류에 반복해서 나오는 사람(예: 매수한 뒤 임대한 사람)을 보관자로 추정해 inferredStakeholder 에 쓰고 그 사람 기준으로 정리합니다.
        - date: 매매·분양·전세·월세는 계약일, 경매는 취득일(잔금 납부일 등). 취득일이 추정이면 dateEstimated=true.
        - amount: 매매는 매매대금, 분양은 분양금액, 경매는 잔금 납부금액(취득금액), 전세·월세는 보증금. monthlyRent 는 월세.
        - periodStart / periodEnd: 전세·월세의 임대차 기간. 그 밖에는 "".
        - ongoing: 오늘 기준으로 아직 이어지는 계약. 매수한 부동산을 아직 팔지 않았으면 그 매수 계약이 진행중,
          임대차는 기간이 끝나지 않았으면 진행중(갱신 계약이 있으면 최신 것만). 매도한 계약은 진행중이 아닙니다.
        - 같은 계약에 대한 서류가 여러 장이면(예: 계약서와 영수증, 매각허가결정과 완납증명) 한 줄로 합치고, 나머지 contractId 는 note 에 "관련 서류 n건"처럼 적습니다.
        - note 는 꼭 필요한 짧은 보충만(갱신, 공동명의, 중도해지 등). 없으면 "".
        - status: 이해관계자가 그 부동산을 지금 보유하고 있거나 임차·임대 중이면 "진행중", 매도를 마쳤거나 임대차가 끝났으면 "완료".
        - 폴더 목록에 있는 모든 폴더에 대해 결과를 돌려줍니다. entries 는 날짜가 빠른 순서로.
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
            "contractType" to Schema.enumeration(CONTRACT_TYPES, "서류 종류"),
            "propertyType" to Schema.string("부동산 종류 (아파트, 오피스텔, 다세대, 단독주택, 상가, 토지 등)"),
            "propertyAddress" to Schema.string("부동산 소재지 전체 주소"),
            "area" to Schema.string("면적 (예: 전용 84.97㎡)"),
            "contractDate" to Schema.string("계약 체결일 YYYY-MM-DD"),
            "leaseStart" to Schema.string("임대차 시작일 YYYY-MM-DD (매매면 인도일)"),
            "leaseEnd" to Schema.string("임대차 종료일 YYYY-MM-DD"),
            "salePrice" to Schema.long("매매대금·분양금액·매각대금(원). 임대차면 0"),
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
            "acquisitionDate" to Schema.string("취득일 YYYY-MM-DD. 없으면 \"\""),
            "acquisitionDateEstimated" to Schema.boolean("취득일이 확실하지 않아 추정한 값이면 true"),
            "acquisitionAmount" to Schema.long("취득금액(원). 경매는 잔금 납부금액, 분양은 분양금액. 임대차면 0"),
            "folderName" to Schema.string("이 부동산의 짧은 이름 (예: 과천 미소가)"),
            "keyFacts" to Schema.string("정리용 핵심 내용 5~10줄"),
            "matchFolderId" to Schema.string("같은 부동산의 기존 폴더 id. 없으면 \"\""),
        ),
    )

    private val entrySchema = Schema.obj(
        mapOf(
            "contractId" to Schema.string("이 줄의 근거가 된 계약서 id"),
            "date" to Schema.string("YYYY-MM-DD"),
            "dateEstimated" to Schema.boolean("날짜가 추정이면 true"),
            "kind" to Schema.enumeration(CONTRACT_TYPES, "계약 종류"),
            "role" to Schema.enumeration(listOf("매수", "매도", "임차", "임대", "기타"), "이해관계자의 역할"),
            "amount" to Schema.long("금액(원)"),
            "monthlyRent" to Schema.long("월세(원). 없으면 0"),
            "periodStart" to Schema.string("임대차 시작일 또는 \"\""),
            "periodEnd" to Schema.string("임대차 종료일 또는 \"\""),
            "ongoing" to Schema.boolean("오늘 기준 진행중이면 true"),
            "note" to Schema.string("짧은 보충. 없으면 \"\""),
        ),
    )

    private val summarySchema = Schema.obj(
        mapOf(
            "inferredStakeholder" to Schema.string("기준으로 삼은 이해관계자 이름"),
            "folders" to Schema.array(
                Schema.obj(
                    mapOf(
                        "folderId" to Schema.string("폴더 id"),
                        "status" to Schema.enumeration(listOf("진행중", "완료"), "이해관계자 기준 상태"),
                        "entries" to Schema.array(entrySchema, "계약별 정리, 날짜 빠른 순"),
                    ),
                ),
            ),
        ),
    )

    private fun model(name: String, schema: Schema, system: String) =
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(
                modelName = name,
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    responseSchema = schema
                    temperature = 0f
                },
                systemInstruction = content { text(system) },
            )

    /** 후보 모델을 차례로 써서 JSON 응답을 받는다. */
    private suspend fun generate(
        context: Context,
        schema: Schema,
        system: String,
        request: Content,
    ): Map<String, Any?> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val remembered = prefs.getString(KEY_MODEL, null)
        val candidates = listOfNotNull(remembered) + MODEL_CANDIDATES.filter { it != remembered }

        var lastError: Exception? = null
        for (name in candidates) {
            try {
                val text = model(name, schema, system).generateContent(request).text
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

    /**
     * 계약서 페이지 사진(JPEG)들을 분석해 Firestore 에 그대로 저장할 수 있는 Map 으로 돌려준다.
     * [folders] 는 같은 부동산 폴더를 찾기 위한 기존 폴더 목록.
     */
    suspend fun analyze(context: Context, pages: List<ByteArray>, folders: List<Folder>): Map<String, Any?> {
        val folderList = if (folders.isEmpty()) {
            "(없음)"
        } else {
            folders.take(200).joinToString("\n") { "- id=${it.id} / 이름=${it.name} / 주소=${it.address}" }
        }
        val request = content {
            pages.forEachIndexed { i, jpeg ->
                text("[${i + 1} / ${pages.size} 페이지]")
                inlineData(jpeg, "image/jpeg")
            }
            text("기존 폴더:\n$folderList\n\n위 서류 사진의 내용을 스키마에 맞춰 정리해 주세요.")
        }
        return generate(context, contractSchema, EXTRACT_PROMPT, request)
    }

    data class SummarizeInput(val folder: Folder, val digests: List<String>)
    data class SummarizeResult(val inferredStakeholder: String, val folders: List<FolderSummary>)

    /** 저장해 둔 핵심 텍스트만으로 폴더들을 이해관계자 기준으로 정리한다 (사진을 다시 보내지 않음). */
    suspend fun summarize(context: Context, stakeholder: String, inputs: List<SummarizeInput>): SummarizeResult {
        val body = buildString {
            appendLine("오늘: ${today()}")
            appendLine("이해관계자: ${stakeholder.ifBlank { "(입력 안 됨 — 추정해 주세요)" }}")
            for (input in inputs) {
                appendLine()
                appendLine("### 폴더 id=${input.folder.id} / 이름=${input.folder.name} / 주소=${input.folder.address}")
                input.digests.forEach { appendLine(it) }
            }
        }
        val json = generate(context, summarySchema, SUMMARIZE_PROMPT, content { text(body) })
        val folders = json.mapList("folders").map { f ->
            FolderSummary(
                folderId = f.str("folderId"),
                status = if (f.str("status") == "완료") FolderStatus.DONE else FolderStatus.ONGOING,
                entries = f.mapList("entries").map(SummaryEntry::from)
                    .sortedBy { it.date.ifBlank { "9999" } },
            )
        }
        return SummarizeResult(json.str("inferredStakeholder"), folders)
    }

    fun friendlyMessage(e: Throwable): String = when (e) {
        is QuotaExceededException -> "오늘 무료 AI 사용량을 다 썼습니다. 내일 다시 시도해 주세요."
        is PromptBlockedException -> "AI가 이 서류의 분석을 거절했습니다. 부동산 서류가 맞는지 확인해 주세요."
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
