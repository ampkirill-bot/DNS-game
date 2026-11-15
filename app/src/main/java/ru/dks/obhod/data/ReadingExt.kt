package ru.dks.obhod.data

import kotlin.math.abs

const val CYLINDER_LIMIT = 2.8

fun isCylinderDeviation(v: Double?): Boolean = v != null && abs(v) > CYLINDER_LIMIT + 1e-9

fun Reading.cylinders(): List<Double?> = listOf(cyl1, cyl2, cyl3, cyl4, cyl5)

fun Reading.hasDeviation(): Boolean = cylinders().any { isCylinderDeviation(it) }

fun Reading.engineFilterDiff(): Double? =
    if (pressureBefore != null && pressureAfter != null) pressureBefore - pressureAfter else null

/** Ввод в течение часа по одному ГПА — одна запись обхода. */
const val ROUND_MS = 60 * 60 * 1000L

/** Последние известные значения по каждому полю (записи — от новых к старым). */
data class KnownValues(
    val cyl: List<Double?>,
    val mskOilLevel: Double?,
    val mskFilterDiff: Double?,
    val engineOilLevel: Double?,
    val pressureBefore: Double?,
    val pressureAfter: Double?,
    val timestamp: Long?,
) {
    fun hasDeviation(): Boolean = cyl.any { isCylinderDeviation(it) }
}

fun knownValues(newestFirst: List<Reading>): KnownValues {
    val c = arrayOfNulls<Double>(5)
    var mo: Double? = null; var md: Double? = null; var eo: Double? = null
    var pb: Double? = null; var pa: Double? = null
    for (r in newestFirst) {
        r.cylinders().forEachIndexed { i, v -> if (c[i] == null && v != null) c[i] = v }
        if (mo == null) mo = r.mskOilLevel
        if (md == null) md = r.mskFilterDiff
        if (eo == null) eo = r.engineOilLevel
        if (pb == null) pb = r.pressureBefore
        if (pa == null) pa = r.pressureAfter
    }
    return KnownValues(c.toList(), mo, md, eo, pb, pa, newestFirst.firstOrNull()?.timestamp)
}

/** Открытая запись обхода: последняя, созданная меньше часа назад (и не раньше «Новой записи»). */
fun openRecord(newestFirst: List<Reading>, roundStart: Long, now: Long = System.currentTimeMillis()): Reading? {
    val l = newestFirst.firstOrNull() ?: return null
    return if (l.timestamp >= maxOf(now - ROUND_MS, roundStart)) l else null
}
