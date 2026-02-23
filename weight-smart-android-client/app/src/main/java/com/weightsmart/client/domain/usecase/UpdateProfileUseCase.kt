package com.weightsmart.client.domain.usecase

import com.weightsmart.client.data.SessionManager
import com.weightsmart.client.domain.model.User
import com.weightsmart.client.domain.repository.AuthRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * UpdateProfileUseCase
 * Applies profile edits (nickname, goal weight, target date) with optimistic local update + server sync.
 *
 * Architecture Role:
 * Domain-layer use case that coordinates the two-phase update:
 * 1. Optimistic local update via [SessionManager.saveUser] -- UI reacts immediately via userFlow.
 * 2. Server sync via [AuthRepository.updateProfile] -- server response overwrites optimistic data.
 * If the server is unreachable, the local update persists; P0 sync reconciles later.
 *
 * @author James Chase
 * @version 2.0
 * @since 2026-01-20
 */
class UpdateProfileUseCase @Inject constructor(
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository
) {
    /**
     * Updates profile fields locally then syncs to the server.
     *
     * @param nickname New display name, or null to keep existing.
     * @param age New age, or null to keep existing.
     * @param height New height, or null to keep existing.
     * @param goalWeight New target weight, or null to keep existing.
     * @param targetDate New goal deadline, or null to keep existing.
     * @return Result containing the server-confirmed User, or failure.
     */
    suspend operator fun invoke(
        nickname: String?,
        age: Int?,
        height: Double?,
        goalWeight: Double?,
        targetDate: LocalDate?
    ): Result<User> {
        val currentUser = sessionManager.getUser()
            ?: return Result.failure(IllegalStateException("Not logged in"))

        // 1. Optimistic local update (UI reacts immediately via userFlow)
        val updated = currentUser.copy(
            nickname = nickname ?: currentUser.nickname,
            age = age ?: currentUser.age,
            height = height ?: currentUser.height,
            goalWeight = goalWeight ?: currentUser.goalWeight,
            targetDate = targetDate ?: currentUser.targetDate
        )
        sessionManager.saveUser(updated)

        // 2. Server sync (best-effort; local update persists regardless)
        return authRepository.updateProfile(
            nickname = nickname,
            age = age,
            height = height,
            goalWeight = goalWeight,
            targetDate = targetDate?.toString()
        )
    }
}
