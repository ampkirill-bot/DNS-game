package ru.dks.obhod.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val signedPattern = Regex("^-?\\d{0,5}(,\\d{0,3})?$")
private val unsignedPattern = Regex("^\\d{0,5}(,\\d{0,3})?$")

/**
 * Фильтр ввода: только цифры, одна запятая и (если разрешено) минус в начале.
 * Точка автоматически превращается в запятую. Возвращает null, если ввод недопустим.
 */
fun filterNumberInput(raw: String, allowNegative: Boolean): String? {
    val s = raw.replace('.', ',').replace(" ", "").replace('−', '-')
    val ok = if (allowNegative) signedPattern.matches(s) else unsignedPattern.matches(s)
    return if (ok) s else null
}

fun toggleSign(s: String): String = if (s.startsWith("-")) s.drop(1) else "-$s"

fun parseNumber(s: String): Double? {
    val t = s.replace(',', '.')
    if (t.isEmpty() || t == "-" || t == "." || t == "-.") return null
    return t.toDoubleOrNull()
}

fun formatNumber(d: Double?): String {
    if (d == null) return ""
    val bd = BigDecimal(d).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros()
    val plain = if (bd.signum() == 0) "0" else bd.toPlainString()
    return plain.replace('.', ',')
}

private val ru = Locale("ru")
fun formatDateTime(ts: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm", ru).format(Date(ts))
fun formatShort(ts: Long): String = SimpleDateFormat("dd.MM HH:mm", ru).format(Date(ts))
