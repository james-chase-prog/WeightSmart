package com.example.weightsmart.domain.usecase

import com.example.weightsmart.data.repository.GoalRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remove the user's goal completely.
 */
@Singleton
class ClearGoalUseCase @Inject constructor(
    private val goals: GoalRepository
) {
    suspend operator fun invoke(userId: Long) = goals.clear(userId)
}
