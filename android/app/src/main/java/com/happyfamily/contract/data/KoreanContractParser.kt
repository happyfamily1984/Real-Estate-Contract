package com.happyfamily.contract.data

/**
 * AI 를 전혀 쓸 수 없을 때, 휴대폰이 사진에서 읽은 글자(OCR)만으로 계약 내용을 규칙대로 뽑는다.
 * AI 만큼 정확하지 않으므로 결과에 "기본 분석" 표시를 하고, AI 를 쓸 수 있게 되면 다시 분석한다.
 * 결과 모양은 AI 분석 결과(GeminiAnalyzer 의 스키마)와 같다.
 */
object KoreanContractParser {

    // ---------- 금액 ----------

    private val KOREAN_DIGITS = mapOf(
        '영' to 0, '공' to 0, '일' to 1, '이' to 2, '삼' to 3, '사' to 4, '오' to 5,
        '육' to 6, '칠' to 7, '팔' to 8, '구' to 9,
    )
    private val SMALL_UNITS = mapOf('십' to 10L, '백' to 100L, '천' to 1_000L)
    private val BIG_UNITS = mapOf('만' to 10_000L, '억' to 100_000_000L, '조' to 1_000_000_000_000L)

    /** "삼억오천만", "3억 5,000만", "350,000,000", "5천만" → 원 단위 숫자. 못 읽으면 null */
    fun parseAmount(raw: String): Long? {
        val s = raw.replace(Regex("[\\s,금원정整₩\\\\()（）]"), "")
        if (s.isEmpty()) return null
        var total = 0L
        var section = 0L
        var num = 0L
        var any = false
        for (ch in s) {
            when {
                ch.isDigit() -> { num = num * 10 + (ch - '0'); any = true }
                ch in KOREAN_DIGITS -> { num = KOREAN_DIGITS.getValue(ch).toLong(); any = true }
                ch in SMALL_UNITS -> {
                    section += (if (num == 0L) 1 else num) * SMALL_UNITS.getValue(ch)
                    num = 0; any = true
                }
                ch in BIG_UNITS -> {
                    val part = section + num
                    total += (if (part == 0L) 1 else part) * BIG_UNITS.getValue(ch)
                    section = 0; num = 0; any = true
                }
                ch == '.' -> return null
                else -> return if (any) total + section + num else null
            }
        }
        return if (any) total + section + num else null
    }

    private val AMOUNT_DIGITS = Regex("[₩\\\\]\\s*([0-9][0-9,]{3,})")
    private val AMOUNT_KOREAN = Regex("금?\\s*([0-9,일이삼사오육칠팔구십백천만억조\\s]{1,30}?)\\s*원")

    /** [labels] 중 하나 뒤쪽 가까운 곳의 금액. 괄호 안 숫자(₩)가 있으면 그것을 믿는다. */
    fun amountAfter(text: String, labels: List<String>, window: Int = 90): Long {
        for (label in labels) {
            var from = 0
            while (true) {
                val i = text.indexOf(label, from)
                if (i < 0) break
                val near = text.substring(i + label.length, minOf(text.length, i + label.length + window))
                AMOUNT_DIGITS.find(near)?.let { m -> parseAmount(m.groupValues[1])?.takeIf { it >= 1000 }?.let { return it } }
                AMOUNT_KOREAN.find(near)?.let { m -> parseAmount(m.groupValues[1])?.takeIf { it >= 1000 }?.let { return it } }
                from = i + label.length
            }
        }
        return 0
    }

    // ---------- 날짜 ----------

    private val DATE = Regex("(\\d{4})\\s*[년.\\-/]\\s*(\\d{1,2})\\s*[월.\\-/]\\s*(\\d{1,2})\\s*일?")

    private fun MatchResult.toIso(): String? {
        val (y, m, d) = destructured
        val mi = m.toInt()
        val di = d.toInt()
        if (y.toInt() !in 1950..2100 || mi !in 1..12 || di !in 1..31) return null
        return "%s-%02d-%02d".format(y, mi, di)
    }

    fun dates(text: String): List<Pair<Int, String>> =
        DATE.findAll(text).mapNotNull { m -> m.toIso()?.let { m.range.first to it } }.toList()

    fun dateAfter(text: String, labels: List<String>, window: Int = 80): String {
        for (label in labels) {
            val i = text.indexOf(label)
            if (i < 0) continue
            val near = text.substring(i, minOf(text.length, i + label.length + window))
            DATE.find(near)?.toIso()?.let { return it }
        }
        return ""
    }

    /** "2021년 5월 1일부터 2023년 4월 30일까지" / "2021.05.01 ~ 2023.04.30" */
    fun period(text: String): Pair<String, String>? {
        val all = DATE.findAll(text).toList()
        for (i in 0 until all.size - 1) {
            val a = all[i]
            val b = all[i + 1]
            val between = text.substring(a.range.last + 1, b.range.first)
            if (between.length <= 20 && (between.contains("~") || between.contains("부터") || between.contains("-"))) {
                val start = a.toIso() ?: continue
                val end = b.toIso() ?: continue
                if (end > start) return start to end
            }
        }
        return null
    }

    // ---------- 그 밖 ----------

    private fun lineAfter(text: String, labels: List<String>): String {
        for (label in labels) {
            val i = text.indexOf(label)
            if (i < 0) continue
            val rest = text.substring(i + label.length).trimStart(' ', ':', '：', '\t', '\n')
            val line = rest.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim()
            if (line.length >= 5) return line.take(80)
        }
        return ""
    }

    private fun nameAfter(text: String, roles: List<String>): String {
        for (role in roles) {
            val m = Regex("$role[\\s\\S]{0,160}?성\\s*명\\s*[:：]?\\s*([가-힣]{2,4}|[가-힣()㈜ ]{2,20}(?:주식회사|㈜))").find(text)
            if (m != null) return m.groupValues[1].trim()
        }
        return ""
    }

    private fun specialTerms(text: String): List<String> {
        val i = text.indexOf("특약")
        if (i < 0) return emptyList()
        return text.substring(i).lineSequence().drop(1)
            .map { it.trim() }
            .takeWhile { !it.contains("증명하기 위하여") && !it.contains("서명") }
            .filter { it.length >= 6 }
            .take(10)
            .toList()
    }

    private val CITY = Regex("([가-힣]{2,4})시(?![가-힣]*특별)")
    private val DONG = Regex("([가-힣]{1,5}동)(?=\\s|\\d|$)")
    private val BUILDING = Regex("([가-힣A-Za-z]{2,10}?)(?:아파트|APT|빌라|오피스텔|맨션|타운)")

    /** 주소로 짧은 폴더 이름 만들기. 예: "경기도 과천시 별양동 미소가아파트" → "과천 미소가" */
    fun folderName(address: String): String {
        if (address.isBlank()) return ""
        val metro = address.contains("특별시") || address.contains("광역시")
        val region = if (!metro) CITY.find(address)?.groupValues?.get(1) else null
        val dong = DONG.find(address)?.groupValues?.get(1)
        val place = region ?: dong ?: address.split(" ").getOrNull(1).orEmpty()
        val building = BUILDING.find(address)?.groupValues?.get(1)
            ?: address.split(" ").firstOrNull { t ->
                t.length in 2..8 && t.all { it in '가'..'힣' } &&
                    !t.endsWith("도") && !t.endsWith("시") && !t.endsWith("구") && !t.endsWith("동") &&
                    !t.endsWith("로") && !t.endsWith("길") && !t.endsWith("읍") && !t.endsWith("면")
            }.orEmpty()
        return listOf(place, building).filter { it.isNotBlank() }.joinToString(" ").take(12)
    }

    private fun contractType(text: String, monthlyRent: Long): String {
        val head = text.take(300)
        return when {
            listOf("매각허가", "낙찰", "매각대금", "대금지급기한", "경매").any { it in text } -> "경매"
            "분양" in text && ("공급" in text || "분양계약" in text) -> "분양"
            "매매계약" in head -> "매매"
            "임대차" in head || "전세" in head || "월세" in head -> if (monthlyRent > 0) "월세" else "전세"
            "임대차" in text -> if (monthlyRent > 0) "월세" else "전세"
            "매매" in text -> "매매"
            else -> "기타"
        }
    }

    /** OCR 글자 → AI 분석 결과와 같은 모양의 Map */
    fun parse(text: String): Map<String, Any?> {
        val compact = text.replace("\r", "")
        val monthlyRent = amountAfter(compact, listOf("차임", "월세", "월 임대료"))
        val type = contractType(compact, monthlyRent)
        val deposit = amountAfter(compact, listOf("보증금"))
        val salePrice = when (type) {
            "분양" -> amountAfter(compact, listOf("분양금액", "분양대금", "공급금액", "공급가액", "분양가"))
            "경매" -> amountAfter(compact, listOf("매각대금", "낙찰가", "매각가격"))
            "매매" -> amountAfter(compact, listOf("매매대금", "매매금액", "매매가"))
            else -> 0
        }
        val down = amountAfter(compact, listOf("계약금"))
        val middle = amountAfter(compact, listOf("중도금"))
        val balance = amountAfter(compact, listOf("잔금", "잔대금"))
        val balanceDate = dateAfter(compact, listOf("잔금", "잔대금"))
        val allDates = dates(compact)
        val contractDate = dateAfter(compact, listOf("계약일", "계약체결일")).ifBlank { allDates.lastOrNull()?.second.orEmpty() }
        val lease = if (type == "전세" || type == "월세") period(compact) else null
        val address = lineAfter(compact, listOf("소재지", "소 재 지", "부동산의 표시", "목적물"))

        var acquisitionDate = ""
        var acquisitionEstimated = false
        var acquisitionAmount = 0L
        when (type) {
            "매매", "분양" -> {
                acquisitionAmount = salePrice
                acquisitionDate = balanceDate
            }
            "경매" -> {
                val paid = dateAfter(compact, listOf("납부일", "완납", "대금지급", "잔금"))
                acquisitionDate = paid.ifBlank { dateAfter(compact, listOf("매각허가", "매각결정")) }
                acquisitionEstimated = paid.isBlank() || !("납부" in compact || "완납" in compact)
                acquisitionAmount = amountAfter(compact, listOf("납부금액", "납부한 금액", "잔금", "잔대금")).takeIf { it > 0 } ?: salePrice
            }
        }

        val owner = nameAfter(compact, listOf("임대인", "매도인", "분양자", "공급자", "채무자", "소유자"))
        val user = nameAfter(compact, listOf("임차인", "매수인", "수분양자", "공급받는자", "낙찰자", "최고가매수인"))

        val cautions = buildList {
            add("AI 를 쓸 수 없어 휴대폰이 글자를 읽어 만든 기본 결과입니다. 금액·날짜·이름을 원본 사진과 꼭 대조하세요.")
            if (address.isBlank()) add("주소를 읽지 못했습니다.")
            if (salePrice == 0L && deposit == 0L) add("주요 금액을 읽지 못했습니다.")
        }
        val summary = "휴대폰 안에서 글자를 읽어 정리한 기본 결과입니다 (AI 미사용). " +
            "AI 를 다시 쓸 수 있게 되면 자동으로 다시 분석합니다."

        return mapOf(
            "contractType" to type,
            "propertyType" to "",
            "propertyAddress" to address,
            "area" to "",
            "contractDate" to contractDate,
            "leaseStart" to (lease?.first ?: ""),
            "leaseEnd" to (lease?.second ?: ""),
            "salePrice" to salePrice,
            "deposit" to deposit,
            "monthlyRent" to monthlyRent,
            "managementFee" to amountAfter(compact, listOf("관리비")),
            "downPayment" to mapOf("amount" to down, "date" to dateAfter(compact, listOf("계약금"))),
            "middlePayments" to if (middle > 0) listOf(mapOf("amount" to middle, "date" to dateAfter(compact, listOf("중도금")))) else emptyList(),
            "balancePayment" to mapOf("amount" to balance, "date" to balanceDate),
            "lessor" to mapOf("name" to owner, "phone" to "", "address" to ""),
            "lessee" to mapOf("name" to user, "phone" to "", "address" to ""),
            "agent" to mapOf("officeName" to "", "representative" to "", "registrationNumber" to "", "phone" to "", "address" to ""),
            "specialTerms" to specialTerms(compact),
            "summary" to summary,
            "cautions" to cautions,
            "acquisitionDate" to acquisitionDate,
            "acquisitionDateEstimated" to acquisitionEstimated,
            "acquisitionAmount" to acquisitionAmount,
            "folderName" to folderName(address),
            "keyFacts" to "휴대폰 글자 인식 결과(앞부분):\n" + compact.take(1500),
            "analysisSource" to "local",
        )
    }
}
