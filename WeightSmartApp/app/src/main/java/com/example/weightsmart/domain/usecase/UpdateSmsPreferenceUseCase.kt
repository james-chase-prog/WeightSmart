package com.example.weightsmart.domain.usecase

import com.example.weightsmart.data.repository.UserRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Update a user's SMS settings (phone number + consent).
 * If phoneNumber is null, number is cleared.
 */
@Singleton
class UpdateSmsPreferenceUseCase @Inject constructor(
    private val users: UserRepository
) {
    suspend operator fun invoke(
        userId: Long,
        phoneNumber: String?,
        consent: Boolean
    ) {
        users.updateSmsSettings(userId, phoneNumber, consent)
    }
}
