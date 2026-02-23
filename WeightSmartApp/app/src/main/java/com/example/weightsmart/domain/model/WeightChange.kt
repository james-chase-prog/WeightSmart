package com.example.weightsmart.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import kotlin.math.abs

/**
 * Computed change between two weight measurements (in pounds).
 *
 * - deltaLb: toLb - fromLb (negative = loss, positive = gain), 1 decimal
 * - daysBetween: fractional days between timestamps
 * - ratePerDayLb: delta per day (can be +/-), 2 decimals
 */
data class WeightChange private constructor(
    val userId: Long,
    val fromId: Long,
    val toId: Long,
    val fromLb: Double,
    val toLb: Double,
    val deltaLb: Double,
    val daysBetween: Double,
    val ratePerDayLb: Double,
    val direction: Direction,
    val fromAt: Instant,
    val toAt: Instant
) {
    enum class Direction { LOSS, GAIN, FLAT }

    companion object {
        private const val SECONDS_PER_DAY = 86_400.0
        private const val EPS = 0.05

        fun of(from: Weight, to: Weight): WeightChange {
            require(from.userId == to.userId) { "Weights must belong to the same user." }
            require(!to.measuredAt.isBefore(from.measuredAt)) { "to.measuredAt must be >= from.measuredAt." }

            val fromLb = round1(from.value)
            val toLb = round1(to.value)
            val delta = round1(toLb - fromLb)

            val seconds = (to.measuredAt.epochSecond - from.measuredAt.epochSecond).toDouble()
            val days = if (seconds <= 0.0) 0.0 else seconds / SECONDS_PER_DAY
            val rate = if (days <= 0.0) 0.0 else round2(delta / days)

            val direction = when {
                abs(delta) <= EPS -> Direction.FLAT
                delta < 0 -> Direction.LOSS
                else -> Direction.GAIN
            }

            return WeightChange(
                userId = from.userId,
                fromId = from.id,
                toId = to.id,
                fromLb = fromLb,
                toLb = toLb,
                deltaLb = delta,
                daysBetween = days,
                ratePerDayLb = rate,
                direction = direction,
                fromAt = from.measuredAt,
                toAt = to.measuredAt
            )
        }

        private fun round1(v: Double): Double =
            BigDecimal(v).setScale(1, RoundingMode.HALF_UP).toDouble()

        private fun round2(v: Double): Double =
            BigDecimal(v).setScale(2, RoundingMode.HALF_UP).toDouble()
    }
}