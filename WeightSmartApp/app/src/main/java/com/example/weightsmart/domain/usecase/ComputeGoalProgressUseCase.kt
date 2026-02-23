package com.example.weightsmart.domain.usecase

import com.example.weightsmart.data.repository.GoalRepository
import com.example.weightsmart.data.repository.WeightRepository
import com.example.weightsmart.domain.model.GoalProgress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Compute progress to goal using the latest weight.
 * Optionally include a starting weight (lb) to get % toward goal.
 *
 * Returns null if no goal or no latest weight exists yet.
 */
@Singleton
class ComputeGoalProgressUseCase @Inject constructor(
    private val goals: GoalRepository,
    private val weights: WeightRepository
) {
    suspend operator fun invoke(
        userId: Long,
        startWeightLb: Double? = null
    ): GoalProgress? {
        val goal = goals.get(userId) ?: return null
        val latest = weights.latest(userId) ?: return null
        return GoalProgress.of(userId, latest.value, goal.valueLb, startWeightLb)
    }
}
