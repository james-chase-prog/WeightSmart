package com.weightsmart.client.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.weightsmart.client.data.local.entity.WeightEntryEntity
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * WeightDatabase
 * The main access point for the underlying SQLite connection.
 *
 * Architecture Role:
 * 1. Schema Definition: Defines the tables (entities) and database versioning.
 * 2. Type Handling: Teaches SQLite how to store complex Java/Kotlin types (like LocalDateTime).
 * 3. DAO Factory: Provides the Data Access Objects to the rest of the app via abstract properties.
 *
 * Key Concepts & Documentation:
 * Room Database: Abstraction layer over SQLite providing compile-time query verification.
 * <a href="https://developer.android.com/training/data-storage/room">Reference: Room Persistence Library</a>
 * TypeConverters: SQLite only supports NULL, INTEGER, REAL, TEXT, BLOB. We must convert Dates to Longs.
 * <a href="https://developer.android.com/training/data-storage/room/referencing-data">Reference: Room TypeConverters</a>
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-01-20
 */
@Database(entities = [WeightEntryEntity::class], version = 1)
@TypeConverters(Converters::class)
abstract class WeightDatabase : RoomDatabase() {
    /** Provides access to the WeightDao for weight entry CRUD operations. */
    abstract val weightDao: WeightDao
}

/**
 * Converters
 * Room TypeConverter class that bridges Kotlin/Java types to SQLite primitives.
 * Registered via @TypeConverters on the WeightDatabase class.
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-01-20
 */
class Converters {
    /**
     * Converts a LocalDateTime to an epoch millisecond Long for SQLite storage.
     * Uses the system default timezone for the conversion.
     *
     * @param date The LocalDateTime to convert, or null.
     * @return Epoch milliseconds, or null if the input is null.
     */
    @TypeConverter
    fun dateToTimestamp(date: LocalDateTime?): Long? {
        return date?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
    }

    /**
     * Converts an epoch millisecond Long back to a LocalDateTime when reading from SQLite.
     * Uses the system default timezone for the conversion.
     *
     * @param value Epoch milliseconds, or null.
     * @return The reconstructed LocalDateTime, or null if the input is null.
     */
    @TypeConverter
    fun fromTimestamp(value: Long?): LocalDateTime? {
        return value?.let {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
        }
    }
}