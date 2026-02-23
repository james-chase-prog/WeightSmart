package com.example.weightsmart.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.min

/**
 * Computed progress toward a single goal weight (in pounds).
 *
 * - remainingLb: how many pounds remain to hit goal (always >= 0, rounded to 1 decimal)
 * - direction: whether the path to goal is to LOSE, GAIN, or you're AT_GOAL
 * - percentTowardGoal: optional % from a starting weight; null when startLb is not provided
 *
 * All weight values use 1-decimal precision.
 */
data class GoalProgress private constructor(
    val userId: Long,
    val currentLb: Double,
    val goalLb: Double,
    val remainingLb: Double,
    val direction: Direction,
    val percentTowardGoal: Double? // 0.0..100.0 (1 decimal) or null
) {
    enum class Direction { LOSE, GAIN, AT_GOAL }

    companion object {
        private const val EPS = 0.05 // tolerance for treating current≈goal

        fun of(
            userId: Long,
            currentLb: Double,
            goalLb: Double,
            startLb: Double? = null // provide if you want percentTowardGoal
        ): GoalProgress {
            val cur = round1(currentLb)
            val goal = round1(goalLb)

            val direction = when {
                abs(cur - goal) <= EPS -> Direction.AT_GOAL
                cur > goal -> Direction.LOSE
                else -> Direction.GAIN
            }

            val remaining = round1(abs(cur - goal))

            val pct: Double? = startLb?.let { s ->
                val start = round1(s)
                val denom = abs(start - goal)
                if (denom <= EPS) {
                    // start equals goal; already at goal
                    100.0
                } else {
                    val raw = (abs(start - cur) / denom) * 100.0
                    round1(min(100.0, raw))
                }
            }

            return GoalProgress(
                userId = userId,
                currentLb = cur,
                goalLb = goal,
                remainingLb = remaining,
                direction = direction,
                percentTowardGoal = pct
            )
        }

        private fun round1(v: Double): Double =
            BigDecimal(v).setScale(1, RoundingMode.HALF_UP).toDouble()
    }
}