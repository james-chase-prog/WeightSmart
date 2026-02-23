package com.example.weightsmart.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per weight measurement (value rounded to 1 decimal).
 * Time stored as UTC epoch seconds.
 */
@Entity(
    tableName = "weights",
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = ["id"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("userId"),
        Index(value = ["userId", "measuredAtEpochSec"])
    ]
)
data class WeightEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: Long,
    val weight: Double,               // ALWAYS store 1-decimal values
    val measuredAtEpochSec: Long      // UTC epoch seconds
)
