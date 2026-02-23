package com.weightsmart.client.domain.usecase

import com.weightsmart.client.data.SessionManager
import javax.inject.Inject

/**
 * GoalProgress
 * Snapshot of the user's progress toward their goal weight.
 *
 * @property currentWeight Most recent logged weight in lbs
 * @property goalWeight Target weight in lbs
 * @property startWeight First recorded weight (null until fetched from repository -- P8)
 * @property percentageComplete Progress as 0-100 integer (placeholder -- P8)
 * @property remainingLb Pounds remaining to reach the goal (positive = still to go)
 */
data class GoalProgress(
    val currentWeight: Double,
    val goalWeight: Double,
    val startWeight: Double?,
    val percentageComplete: Int,
    val remainingLb: Double
)

/**
 * ComputeGoalProgressUseCase
 * Calculates how far the user is from their goal weight.
 *
 * Architecture Role:
 * Domain-layer use case consumed by the Analytics Fragment (P8).
 * Currently provides a stub implementation -- percentage math and
 * start-weight fetching are deferred to the P8 analytics sprint.
 *
 * Key Concepts & Documentation:
 * Start Weight: Will be fetched from WeightRepository.getFirstWeight() once
 *   the analytics ViewModel is built (P8). For now, defaults to 0.0.
 * <a href="https://developer.android.com/topic/architecture/domain-layer">Reference: Domain Layer</a>
 *
 * @author James Chase
 * @version 1.1 (P5: invoke() changed to suspend for DataStore SessionManager)
 * @since 2026-01-20
 */
class ComputeGoalProgressUseCase @Inject constructor(
    private val sessionManager: SessionManager
) {
    /**
     * Computes goal progress from the session cache.
     * @return [GoalProgress] snapshot, or null if no goal or current weight is set
     */
    suspend operator fun invoke(): GoalProgress? {
        val user = sessionManager.getUser() ?: return null

        val current = user.currentWeight ?: return null
        val goal = user.goalWeight ?: return null

        // Guard against division-by-zero or invalid states
        if (goal == 0.0 || current == 0.0) return null

        val remaining = current - goal

        // Temporary placeholder until start-weight fetching is wired (P8)
        val start = 0.0 // TODO: Fetch "Start Weight" from WeightRepository.getFirstWeight()

        return GoalProgress(
            currentWeight = current,
            goalWeight = goal,
            startWeight = start,
            percentageComplete = 0, // TODO: Implement percentage math (P8)
            remainingLb = remaining
        )
    }
}