package com.example.weightsmart.domain.usecase

import com.example.weightsmart.data.repository.UserRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateProfileUseCase @Inject constructor(
    private val users: UserRepository
) {
    suspend operator fun invoke(
        userId: Long,
        nickname: String?,
        avatar: String? // for now we store a URI/String; full storage later
    ) {
        users.updateProfile(
            userId = userId,
            nickname = nickname,
            phoneNumber = null,   // unchanged here
            smsConsent = null,    // unchanged here
            avatar = avatar
        )
    }
}
