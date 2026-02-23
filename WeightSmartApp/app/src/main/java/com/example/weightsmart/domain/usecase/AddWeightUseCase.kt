package com.example.weightsmart.domain.usecase

import com.example.weightsmart.data.repository.WeightRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Add a weight for a user (in pounds, one-decimal enforced in repo) with a timestamp.
 * Also updates the user's currentWeight snapshot.
 */
@Singleton
class AddWeightUseCase @Inject constructor(
    private val weights: WeightRepository
) {
    /**
     * @param userId target user
     * @param valueLb weight value in pounds (e.g., 124.2)
     * @param measuredAt when the weight was taken; defaults to now
     * @return the inserted entry id
     */
    suspend operator fun invoke(
        userId: Long,
        valueLb: Double,
        measuredAt: Instant = Instant.now()
    ): Long = weights.addWeight(
        userId = userId,
        value = valueLb,
        measuredAtEpochSec = measuredAt.epochSecond
    )
}
