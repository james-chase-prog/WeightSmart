package com.weightsmart.client.di

import android.content.Context
import androidx.room.Room
import com.weightsmart.client.data.local.WeightDao
import com.weightsmart.client.data.local.WeightDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * DatabaseModule
 * Hilt module responsible for creating the local SQLite database and exposing DAOs.
 *
 * Architecture Role:
 * Provides the local persistence layer for the offline-first architecture.
 * Creates a single Room database instance ("weight_smart_db") and exposes
 * the WeightDao so repositories can query/insert weight entries without
 * depending on the database directly.
 *
 * Key Concepts & Documentation:
 * Room Persistence Library: An abstraction layer over SQLite providing compile-time SQL verification.
 * <a href="https://developer.android.com/training/data-storage/room">Reference: Room Library</a>
 * DAO (Data Access Object): Defines SQL queries as annotated Kotlin methods.
 * <a href="https://developer.android.com/training/data-storage/room/accessing-data">Reference: Accessing Data using Room DAOs</a>
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-01-20
 */
@Module
@InstallIn(SingletonComponent::class) // Live as long as the app lives
object DatabaseModule {

    /**
     * Provides the WeightDatabase singleton (creates database connection).
     * Satisfies: WeightDao extraction and any future DAO additions.
     * The database file is named "weight_smart_db" in the app's private storage.
     */
    @Provides
    @Singleton
    fun provideWeightDatabase(
        @ApplicationContext context: Context
    ): WeightDatabase {
        return Room.databaseBuilder(
            context,
            WeightDatabase::class.java,
            "weight_smart_db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    /**
     * Extracts the WeightDao from the database.
     * Satisfies: WeightRepository, SyncWorker, and any component that reads/writes weight entries.
     * Consumers should depend on the DAO (not the Database) to follow the principle of least privilege.
     */
    @Provides
    @Singleton
    fun provideWeightDao(database: WeightDatabase): WeightDao {
        return database.weightDao
    }
}