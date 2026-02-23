package com.example.weightsmart.domain.usecase

import com.example.weightsmart.data.repository.GoalRepository
import com.example.weightsmart.domain.model.GoalWeight
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Set (or replace) the user's goal weight.
 * Returns the stored domain model.
 */
@Singleton
class SetGoalUseCase @Inject constructor(
    private val goals: GoalRepository
) {
    suspend operator fun invoke(
        userId: Long,
        goalLb: Double,
        goalDate: Instant? = null,
        notes: String? = null
    ): GoalWeight = goals.setGoal(
        userId = userId,
        goalWeight = goalLb,
        goalDateEpochSec = goalDate?.epochSecond,
        notes = notes
    )
}
