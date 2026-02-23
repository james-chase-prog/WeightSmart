
package com.example.weightsmart.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.weightsmart.data.dao.*
import com.example.weightsmart.data.entity.*

@Database(
    entities = [
        UserEntity::class,
        WeightEntryEntity::class,
        GoalWeightEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun weightDao(): WeightDao
    abstract fun goalWeightDao(): GoalWeightDao
}