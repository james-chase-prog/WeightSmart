package com.example.weightsmart.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * One goal per user.
 * We make userId the PRIMARY KEY so inserting with REPLACE semantics
 * overwrites the previous goal row for that user.
 *
 * goalDateEpochSec is nullable by design.
 */
@Entity(
    tableName = "goals",
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = ["id"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class GoalWeightEntity(
    @PrimaryKey val userId: Long,              // one row per user
    val goalWeight: Double,                    // store with 1-decimal rounding at repo level
    val goalDateEpochSec: Long? = null,        // nullable target date
    val notes: String? = null,
    val updatedAtEpochSec: Long = System.currentTimeMillis() / 1000
)
