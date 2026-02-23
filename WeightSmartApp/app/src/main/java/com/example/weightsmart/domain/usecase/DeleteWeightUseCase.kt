package com.example.weightsmart.domain.usecase

import com.example.weightsmart.data.repository.WeightRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Delete a weight entry. If it was the latest, recompute the user's currentWeight snapshot.
 */
@Singleton
class DeleteWeightUseCase @Inject constructor(
    private val weights: WeightRepository
) {
    suspend operator fun invoke(entryId: Long) = weights.deleteWeight(entryId)
}
