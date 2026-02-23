package com.weightsmart.client.domain.usecase

import com.weightsmart.client.data.repository.WeightRepository
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * AddWeightUseCase
 * Encapsulates the business rule for logging a new weight entry.
 *
 * Architecture Role:
 * Domain-layer use case invoked by ViewModels. Converts the legacy Instant
 * timestamp to LocalDateTime, then delegates to WeightRepository which
 * handles both the remote API call and local Room insert (offline-first).
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-01-20
 */
class AddWeightUseCase @Inject constructor(
    private val repository: WeightRepository
) {
    /**
     * Logs a weight entry for the current user.
     * @param weight The weight value in pounds
     * @param date The timestamp of the recording (defaults to now)
     */
    suspend operator fun invoke(weight: Double, date: Instant = Instant.now()) {
        // Convert legacy Instant to LocalDateTime (repository and Room use java.time)
        val localDateTime = date.atZone(ZoneId.systemDefault()).toLocalDateTime()

        // Repository handles API call + local DB insert (offline-first)
        repository.addWeight(weight, localDateTime)
    }
}