package com.example.weightsmart.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * Domain model for a single weight measurement.
 * - value: one decimal place (e.g., 124.2)
 * - measuredAt: exact timestamp of when the weight was taken
 */
data class Weight private constructor(
    val id: Long,
    val userId: Long,
    val value: Double,           // always rounded to 1 decimal place
    val measuredAt: Instant
) {
    companion object {
        /** Factory enforces one-decimal precision with HALF_UP rounding. */
        fun of(
            id: Long = 0L,
            userId: Long,
            value: Double,
            measuredAt: Instant
        ): Weight {
            val rounded = BigDecimal(value).setScale(1, RoundingMode.HALF_UP).toDouble()
            return Weight(id, userId, rounded, measuredAt)
        }
    }
}