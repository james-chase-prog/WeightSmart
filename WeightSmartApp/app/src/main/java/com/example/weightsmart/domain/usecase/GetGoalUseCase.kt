package com.example.weightsmart.domain.usecase

import com.example.weightsmart.data.repository.GoalRepository
import com.example.weightsmart.domain.model.GoalWeight
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Get the (single) goal for a user, or null if none set.
 */
@Singleton
class GetGoalUseCase @Inject constructor(
    private val goals: GoalRepository
) {
    suspend operator fun invoke(userId: Long): GoalWeight? = goals.get(userId)
}
