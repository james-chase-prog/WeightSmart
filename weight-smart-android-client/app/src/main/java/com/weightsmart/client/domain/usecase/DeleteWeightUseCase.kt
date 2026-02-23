package com.weightsmart.client.domain.usecase

import com.weightsmart.client.data.repository.WeightRepository
import javax.inject.Inject

/**
 * DeleteWeightUseCase
 * Encapsulates the business rule for removing a weight entry.
 *
 * Architecture Role:
 * Domain-layer use case invoked by ViewModels. Delegates to WeightRepository
 * which performs a soft-delete locally (tombstone for sync) and issues a
 * server DELETE when online.
 *
 * Note: The UI currently passes a Long ID, but the system uses String UUIDs.
 * The Adapter layer needs refactoring to pass the String ID directly.
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-01-20
 */
class DeleteWeightUseCase @Inject constructor(
    private val repository: WeightRepository
) {
    /**
     * Deletes the weight entry identified by [weightId].
     * @param weightId UUID string of the entry to delete
     */
    suspend operator fun invoke(weightId: String) {
        repository.deleteWeight(weightId)
    }
}