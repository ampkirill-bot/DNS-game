package ru.dks.obhod.data

import kotlin.math.abs

const val CYLINDER_LIMIT = 2.8

fun isCylinderDeviation(v: Double?): Boolean = v != null && abs(v) > CYLINDER_LIMIT + 1e-9

fun Reading.cylinders(): List<Double?> = listOf(cyl1, cyl2, cyl3, cyl4, cyl5)

fun Reading.hasDeviation(): Boolean = cylinders().any { isCylinderDeviation(it) }

fun Reading.engineFilterDiff(): Double? =
    if (pressureBefore != null && pressureAfter != null) pressureBefore - pressureAfter else null
