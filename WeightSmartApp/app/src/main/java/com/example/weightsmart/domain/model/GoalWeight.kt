package com.example.weightsmart.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * Single goal-weight per user (in **pounds**), with optional target date.
 * Rounds to one decimal place, same as Weight.
 */
data class GoalWeight private constructor(
    val userId: Long,
    val valueLb: Double,          // one decimal place
    val goalDate: Instant?,       // nullable target date
    val notes: String?,
    val updatedAt: Instant        // when this goal was last set/changed
) {
    companion object {
        fun of(
            userId: Long,
            valueLb: Double,
            goalDate: Instant? = null,
            notes: String? = null,
            updatedAt: Instant = Instant.now()
        ): GoalWeight {
            val rounded = BigDecimal(valueLb).setScale(1, RoundingMode.HALF_UP).toDouble()
            return GoalWeight(
                userId = userId,
                valueLb = rounded,
                goalDate = goalDate,
                notes = notes?.trim()?.ifBlank { null },
                updatedAt = updatedAt
            )
        }
    }

    fun withValue(newValueLb: Double, at: Instant = Instant.now()): GoalWeight =
        of(userId, newValueLb, goalDate, notes, at)

    fun withGoalDate(newGoalDate: Instant?, at: Instant = Instant.now()): GoalWeight =
        of(userId, valueLb, newGoalDate, notes, at)

    fun withNotes(newNotes: String?, at: Instant = Instant.now()): GoalWeight =
        of(userId, valueLb, goalDate, newNotes, at)
}