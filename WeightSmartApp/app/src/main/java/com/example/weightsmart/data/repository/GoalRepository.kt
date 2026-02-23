package com.example.weightsmart.data.repository

import com.example.weightsmart.data.dao.GoalWeightDao
import com.example.weightsmart.data.entity.GoalWeightEntity
import com.example.weightsmart.data.mapper.toDomain
import com.example.weightsmart.domain.model.GoalWeight
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalRepository @Inject constructor(
    private val goalDao: GoalWeightDao
) {
    /**
     * Set (or replace) the user's goal and return the domain model.
     * Under the hood, GoalWeightEntity uses userId as PRIMARY KEY with REPLACE semantics.
     */
    suspend fun setGoal(
        userId: Long,
        goalWeight: Double,
        goalDateEpochSec: Long? = null,
        notes: String? = null
    ): GoalWeight {
        val rounded = round1(goalWeight)
        goalDao.upsert(
            GoalWeightEntity(
                userId = userId,
                goalWeight = rounded,
                goalDateEpochSec = goalDateEpochSec,
                notes = notes
            )
        )
        // fetch post-upsert and map to domain
        return requireNotNull(goalDao.getForUser(userId)).toDomain()
    }

    /** Retrieve the (single) goal for a user as a domain model, or null if none set. */
    suspend fun get(userId: Long): GoalWeight? =
        goalDao.getForUser(userId)?.toDomain()

    /** Clear the user's goal entirely. */
    suspend fun clear(userId: Long) =
        goalDao.deleteForUser(userId)

    private fun round1(v: Double): Double =
        BigDecimal(v).setScale(1, RoundingMode.HALF_UP).toDouble()
}
