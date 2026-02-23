package com.example.weightsmart.data.dao

import androidx.room.*
import com.example.weightsmart.data.entity.WeightEntryEntity

@Dao
interface WeightDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: WeightEntryEntity): Long

    @Update
    suspend fun update(entry: WeightEntryEntity)

    @Delete
    suspend fun delete(entry: WeightEntryEntity)

    @Query("SELECT * FROM weights WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): WeightEntryEntity?

    @Query("""
        SELECT * FROM weights
        WHERE userId = :userId
        ORDER BY measuredAtEpochSec DESC
    """)
    suspend fun getAllForUserDesc(userId: Long): List<WeightEntryEntity>

    @Query("""
        SELECT * FROM weights
        WHERE userId = :userId
        ORDER BY measuredAtEpochSec DESC
        LIMIT 1
    """)
    suspend fun latestForUser(userId: Long): WeightEntryEntity?
}
