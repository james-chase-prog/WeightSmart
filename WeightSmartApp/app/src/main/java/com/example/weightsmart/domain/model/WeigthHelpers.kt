package com.example.weightsmart.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DEFAULT_WEIGHT_DT_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")

/** Round a Double to 1 or 2 decimals (HALF_UP). */
fun Double.round1(): Double = BigDecimal(this).setScale(1, RoundingMode.HALF_UP).toDouble()
fun Double.round2(): Double = BigDecimal(this).setScale(2, RoundingMode.HALF_UP).toDouble()

/** Common Weight display helpers. */
fun Weight.lbString(): String = "${value.round1()} lb"

fun Weight.dateTimeString(
    zoneId: ZoneId = ZoneId.systemDefault(),
    formatter: DateTimeFormatter = DEFAULT_WEIGHT_DT_FORMAT
): String = LocalDateTime.ofInstant(measuredAt, zoneId).format(formatter)

fun Weight.isNewerThan(other: Weight): Boolean = measuredAt >= other.measuredAt
