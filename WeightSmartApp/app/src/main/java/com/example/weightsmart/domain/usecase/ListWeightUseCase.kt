package com.example.weightsmart.domain.usecase

import com.example.weightsmart.data.repository.WeightRepository
import com.example.weightsmart.domain.model.Weight
import javax.inject.Inject
import javax.inject.Singleton

/**
 * List all weights for a user (newest first) as domain models suitable for UI.
 */
@Singleton
class ListWeightsUseCase @Inject constructor(
    private val weights: WeightRepository
) {
    suspend operator fun invoke(userId: Long): List<Weight> =
        weights.listForUserDesc(userId)
}
