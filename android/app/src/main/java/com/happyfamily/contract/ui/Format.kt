package com.happyfamily.contract.ui

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

/** 350000000 → "3억 5,000만원", 1500000 → "150만원", 0 → "-" */
fun formatWon(amount: Long): String {
    if (amount <= 0L) return "-"
    val eok = amount / 100_000_000
    val man = (amount % 100_000_000) / 10_000
    val won = amount % 10_000
    val parts = buildList {
        if (eok > 0) add("${numberFormat.format(eok)}억")
        if (man > 0) add("${numberFormat.format(man)}만")
        if (won > 0) add(numberFormat.format(won))
    }
    return parts.joinToString(" ") + "원"
}

/** "3억 5,000만원 (350,000,000원)" */
fun formatWonDetailed(amount: Long): String =
    if (amount <= 0L) "-" else "${formatWon(amount)} (${numberFormat.format(amount)}원)"

/** "2026-03-15" → "2026. 3. 15." */
fun formatDate(value: String): String {
    val parts = value.split("-")
    if (parts.size != 3) return value.ifBlank { "-" }
    val (y, m, d) = parts
    return "$y. ${m.trimStart('0')}. ${d.trimStart('0')}."
}

fun formatDate(date: Date?): String =
    date?.let { SimpleDateFormat("yyyy. M. d.", Locale.KOREA).format(it) } ?: ""
