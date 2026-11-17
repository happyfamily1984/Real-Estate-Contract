package com.happyfamily.contract.ui

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

/** 350000000 → "3억 5,000만원" */
fun formatWon(amount: Long): String = com.happyfamily.contract.data.formatWonKr(amount)

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
