package com.example.weightsmart.domain.usecase

import com.example.weightsmart.data.repository.UserRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetUserUseCase @Inject constructor(
    private val users: UserRepository
) {
    data class Result(
        val id: Long,
        val username: String,
        val nickname: String?,
        val avatar: String?,
        val currentWeight: Double?,          // <- added
        val currentWeightEpochSec: Long?     // <- added
    )

    suspend operator fun invoke(userId: Long): Result? =
        users.getById(userId)?.let { u ->
            Result(
                id = u.id,
                username = u.username,
                nickname = u.nickname,
                avatar = u.avatar,
                currentWeight = u.currentWeight,
                currentWeightEpochSec = u.currentWeightEpochSec
            )
        }
}
