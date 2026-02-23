package com.example.weightsmart.data.repository

import androidx.room.withTransaction
import com.example.weightsmart.data.AppDatabase
import com.example.weightsmart.data.dao.UserDao
import com.example.weightsmart.data.dao.WeightDao
import com.example.weightsmart.data.entity.WeightEntryEntity
import com.example.weightsmart.data.mapper.toDomain
import com.example.weightsmart.domain.model.Weight
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeightRepository @Inject constructor(
    private val db: AppDatabase,
    private val weightDao: WeightDao,
    private val userDao: UserDao
) {

    /** Add a weight and update the user's current weight snapshot atomically. */
    suspend fun addWeight(
        userId: Long,
        value: Double,
        measuredAtEpochSec: Long = System.currentTimeMillis() / 1000
    ): Long = db.withTransaction {
        val rounded = round1(value)
        val id = weightDao.insert(
            WeightEntryEntity(
                userId = userId,
                weight = rounded,
                measuredAtEpochSec = measuredAtEpochSec
            )
        )
        updateUserSnapshotToLatest(userId)
        id
    }

    /** Delete a weight; if it was the latest, recompute snapshot. */
    suspend fun deleteWeight(entryId: Long) = db.withTransaction {
        val entry = weightDao.getById(entryId) ?: return@withTransaction
        val userId = entry.userId
        weightDao.delete(entry)
        updateUserSnapshotToLatest(userId)
    }

    /** Latest weight for a user (domain model). */
    suspend fun latest(userId: Long): Weight? =
        weightDao.latestForUser(userId)?.toDomain()

    /** Weights list (newest first) for RecyclerView (domain models). */
    suspend fun listForUserDesc(userId: Long): List<Weight> =
        weightDao.getAllForUserDesc(userId).map { it.toDomain() }

    // ---- helpers ----

    private suspend fun updateUserSnapshotToLatest(userId: Long) {
        val latest = weightDao.latestForUser(userId)
        userDao.updateCurrentWeightSnapshot(
            userId = userId,
            weight = latest?.weight,
            epochSec = latest?.measuredAtEpochSec
        )
    }

    private fun round1(v: Double): Double =
        BigDecimal(v).setScale(1, RoundingMode.HALF_UP).toDouble()
}
