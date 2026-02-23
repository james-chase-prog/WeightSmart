package com.weightsmart.client.domain.usecase

import com.weightsmart.client.data.SessionManager
import com.weightsmart.client.data.repository.WeightRepository
import javax.inject.Inject

/**
 * GoalReachedUseCase
 * Determines whether a newly logged weight means the user has hit their goal.
 *
 * Architecture Role:
 * Domain-layer use case containing the core celebration business rule.
 * Called by HomeViewModel after every weight log to decide whether to fire
 * a goal-reached notification. Infers goal direction (loss vs. gain) from
 * data rather than requiring an explicit user-selected mode.
 *
 * Direction Logic:
 *   firstWeight > goalWeight  -->  user is losing  -->  celebrate when newWeight <= goalWeight
 *   firstWeight <= goalWeight -->  user is gaining  -->  celebrate when newWeight >= goalWeight
 *
 * Returns false (no celebration) when:
 *   - No goal weight is set
 *   - No weight history exists (cannot determine direction)
 *   - Goal has already been celebrated (guard resets when goal weight changes)
 *
 * Key Concepts & Documentation:
 * Celebration Guard: SessionManager stores the goal value that was celebrated.
 *   If the user changes their goal, the guard auto-resets so a new celebration can fire.
 * <a href="https://developer.android.com/topic/architecture/domain-layer">Reference: Domain Layer</a>
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-02-04
 */
class GoalReachedUseCase @Inject constructor(
    private val sessionManager: SessionManager,
    private val weightRepository: WeightRepository
) {
    /**
     * Checks if [newWeight] means the user has reached their goal.
     * Makes a network call (with local fallback) to fetch the first recorded weight.
     * If the goal is reached, marks it as celebrated so subsequent logs don't re-fire.
     * The flag auto-resets when the user changes their goal weight.
     */
    suspend operator fun invoke(newWeight: Double): Boolean {
        val user = sessionManager.getUser() ?: return false
        val goalWeight = user.goalWeight ?: return false

        // Already celebrated for this goal — don't fire again
        if (sessionManager.isGoalAlreadyReached(goalWeight)) return false

        val firstWeight = weightRepository.getFirstWeight() ?: return false

        val goalIsWeightLoss = firstWeight > goalWeight

        val reached = if (goalIsWeightLoss) {
            newWeight <= goalWeight
        } else {
            newWeight >= goalWeight
        }

        if (reached) {
            sessionManager.markGoalReached(goalWeight)
        }

        return reached
    }
}
