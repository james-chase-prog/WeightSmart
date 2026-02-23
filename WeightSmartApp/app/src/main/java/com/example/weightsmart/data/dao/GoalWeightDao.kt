package com.example.weightsmart.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.weightsmart.data.entity.GoalWeightEntity

@Dao
interface GoalWeightDao {

    /**
     * Insert or replace the user's goal.
     * Because userId is the PRIMARY KEY, REPLACE means "delete old row, insert new one".
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: GoalWeightEntity): Long

    @Query("SELECT * FROM goals WHERE userId = :userId LIMIT 1")
    suspend fun getForUser(userId: Long): GoalWeightEntity?

    @Query("DELETE FROM goals WHERE userId = :userId")
    suspend fun deleteForUser(userId: Long)
}
