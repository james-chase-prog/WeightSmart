package com.example.weightsmart.domain.model

import kotlin.math.abs

// NOTE: round1() / round2() come from WeigthHelpers.kt. Do not redefine them here.

fun WeightChange.isGain(): Boolean = direction == WeightChange.Direction.GAIN
fun WeightChange.isLoss(): Boolean = direction == WeightChange.Direction.LOSS
fun WeightChange.isFlat(): Boolean = direction == WeightChange.Direction.FLAT

fun WeightChange.deltaLabel(): String {
    val sign = when {
        deltaLb > 0 -> "+"
        deltaLb < 0 -> "−" // U+2212
        else -> ""
    }
    val absVal = abs(deltaLb).round1()
    return "$sign$absVal lb"
}

fun WeightChange.ratePerDayLabel(): String {
    val sign = when {
        ratePerDayLb > 0 -> "+"
        ratePerDayLb < 0 -> "−"
        else -> ""
    }
    val absVal = abs(ratePerDayLb).round2()
    return "$sign$absVal lb/day"
}

fun WeightChange.ratePerWeekLabel(): String {
    val weekly = ratePerDayLb * 7.0
    val sign = when {
        weekly > 0 -> "+"
        weekly < 0 -> "−"
        else -> ""
    }
    val absVal = abs(weekly).round2()
    return "$sign$absVal lb/week"
}
