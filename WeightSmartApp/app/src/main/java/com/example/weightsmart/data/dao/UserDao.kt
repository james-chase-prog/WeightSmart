package com.example.weightsmart.data.dao

import androidx.room.*
import com.example.weightsmart.data.entity.UserEntity

@Dao
interface UserDao {
    // Create
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: UserEntity): Long

    // Read
    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): UserEntity?

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int

    // Update (full-row)
    @Update
    suspend fun update(user: UserEntity)

    // Targeted updates
    @Query("""
        UPDATE users
        SET currentWeight = :weight, currentWeightEpochSec = :epochSec
        WHERE id = :userId
    """)
    suspend fun updateCurrentWeightSnapshot(
        userId: Long,
        weight: Double?,
        epochSec: Long?
    )

    @Query("""
        UPDATE users
        SET phoneNumber = :phoneNumber, smsConsent = :smsConsent
        WHERE id = :userId
    """)
    suspend fun updateSmsSettings(
        userId: Long,
        phoneNumber: String?,
        smsConsent: Boolean
    )

    // Delete
    @Delete
    suspend fun delete(user: UserEntity)
}
