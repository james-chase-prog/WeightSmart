package com.weightsmart.client.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.weightsmart.client.data.local.entity.WeightEntryEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

/**
 * WeightDao
 * Data Access Object (DAO) for the 'weight_entries' table.
 *
 * Architecture Role:
 * 1. Abstraction: Hides raw SQL queries behind clean Kotlin function calls.
 * 2. Sync Management: Handles the "Dirty Flags" (isSynced) used by the Background Worker to upload data.
 *
 * Key Concepts & Documentation:
 * Kotlin Flow: A cold asynchronous data stream that sequentially emits values. Used here for Live Updates.
 * <a href="https://developer.android.com/kotlin/flow">Reference: Kotlin Flow</a>
 * Suspend Functions: Database I/O is heavy. Suspend functions ensure these run off the Main Thread.
 *
 * @author James Chase
 * @version 1.1
 * @since 2026-01-20
 */
@Dao
interface WeightDao {

    // --- WRITE OPERATIONS ---

    /**
     * UPSERT: Inserts or Updates.
     * Strategy: OnConflictStrategy.REPLACE
     * Logic: If an ID already exists, overwrite it. If we edit a weight, we set 'isSynced = 0'
     * externally (in the Repo) so the background worker knows to upload the changes.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeight(entry: WeightEntryEntity)

    /**
     * SOFT DELETE: The "User Action".
     * Logic: We don't remove the row immediately. We mark it 'isDeleted=1' and 'isSynced=0'.
     * This tells the SyncWorker: "Tell the server to delete this ID".
     */
    @Query("UPDATE weight_entries SET isDeleted = 1, isSynced = 0, updatedAt = :timestamp WHERE id = :id")
    suspend fun softDeleteWeight(id: String, timestamp: Long = System.currentTimeMillis())

    /**
     * HARD DELETE: The "System Cleanup".
     * Logic: PERMANENTLY removes the row from SQLite.
     * WARNING: Only call this AFTER the Server confirms it has processed the deletion (204 No Content).
     * If called before syncing, the server will never know it was deleted (Zombie Data).
     */
    @Query("DELETE FROM weight_entries WHERE id = :id")
    suspend fun hardDeleteWeight(id: String)

    // --- READ OPERATIONS ---

    /**
     * GET ALL (Flow): Standard List.
     * Logic: Returns the full list of weights. Useful for small datasets or calculating local stats
     * where pagination is overkill. Emits updates automatically on DB changes.
     */
    @Query("SELECT * FROM weight_entries WHERE userId = :userId AND isDeleted = 0 ORDER BY date DESC")
    fun getWeightsForUser(userId: Long): Flow<List<WeightEntryEntity>>

    /**
     * GRAPH DATA: Date Range Filtering.
     * Logic: Fetches non-deleted weights between two specific timestamps.
     * Used for "Last 30 Days" or "This Year" graphs. Ordered ASC for plotting time series.
     */
    @Query("""
        SELECT * FROM weight_entries 
        WHERE userId = :userId 
        AND isDeleted = 0 
        AND date BETWEEN :startDate AND :endDate 
        ORDER BY date ASC
    """)
    fun getWeightsInDateRange(userId: Long, startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<WeightEntryEntity>>

    /**
     * CURRENT STATUS: Most Recent Entry.
     * Logic: Fetches exactly one record—the most recent one that hasn't been deleted.
     * Used for the "Current Weight" Dashboard Card.
     */
    @Query("SELECT * FROM weight_entries WHERE userId = :userId AND isDeleted = 0 ORDER BY date DESC LIMIT 1")
    fun getMostRecentWeight(userId: Long): Flow<WeightEntryEntity?>

    // --- SYNC OPERATIONS ---

    /**
     * SYNC HELPER: Find Dirty Records.
     * Logic: Fetches all records where isSynced = 0.
     * This includes "New/Edited" records (isDeleted=0) and "Pending Deletion" records (isDeleted=1).
     */
    @Query("SELECT * FROM weight_entries WHERE isSynced = 0")
    suspend fun getUnsyncedWeights(): List<WeightEntryEntity>

    /**
     * BULK INSERT: Initial sync population.
     * Uses REPLACE strategy to handle any conflicts gracefully.
     * Called when syncing pages of weight entries from server.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<WeightEntryEntity>)

    /**
     * CLEANUP: Remove tombstones that have been synced.
     * Called after successful sync to clear soft-deleted records that
     * the server has acknowledged.
     */
    @Query("DELETE FROM weight_entries WHERE isDeleted = 1 AND isSynced = 1")
    suspend fun cleanupSyncedTombstones()

    /**
     * OLDEST ENTRY: First recorded weight for a user.
     * Used by GoalReachedUseCase to infer weight-loss vs weight-gain direction.
     * Returns null if no entries exist yet.
     */
    @Query("SELECT * FROM weight_entries WHERE userId = :userId AND isDeleted = 0 ORDER BY date ASC LIMIT 1")
    suspend fun getOldestWeight(userId: Long): WeightEntryEntity?
}