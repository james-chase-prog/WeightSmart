package com.weightsmart.client.platform.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.weightsmart.client.data.SessionManager
import com.weightsmart.client.data.local.WeightDao
import com.weightsmart.client.data.mappers.toEntity
import com.weightsmart.client.data.remote.WeightSmartApi
import com.weightsmart.client.data.remote.dto.WeightLogRequest
import java.time.format.DateTimeFormatter

/**
 * SyncWorker
 * Background worker that synchronizes local weight data with the server.
 *
 * Architecture Role:
 * Implements the 3-phase "Offline-First" sync strategy executed by WorkManager:
 *   Phase 1 - PUSH: Upload all unsynced local changes (creates and soft-deletes)
 *   Phase 2 - PULL: Download changes from server (initial 100-record pull or delta sync)
 *   Phase 3 - CLEANUP: Remove synced tombstones from local Room DB
 *
 * Lifecycle & Scheduling:
 * Created by SyncWorkerFactory (manual injection, not @HiltWorker).
 * Scheduled by SyncManager as either one-time (login/connectivity restore)
 * or periodic (every 15 minutes). Retries up to 3 times on transient failures
 * with exponential backoff handled by WorkManager.
 *
 * Key Concepts & Documentation:
 * CoroutineWorker: WorkManager worker that runs in a coroutine (non-blocking).
 * <a href="https://developer.android.com/topic/libraries/architecture/workmanager/advanced/coroutineworker">Reference: CoroutineWorker</a>
 * Manual WorkerFactory: Used instead of @HiltWorker due to kapt/Kotlin 2.x metadata incompatibility.
 * <a href="https://developer.android.com/topic/libraries/architecture/workmanager/advanced/custom-configuration">Reference: Custom WorkerFactory</a>
 * Server-Wins Conflict Resolution: On ID collision, the server's record overwrites the local one.
 *
 * @author James Chase
 * @version 1.1
 * @since 2026-02-04
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val api: WeightSmartApi,
    private val dao: WeightDao,
    private val sessionManager: SessionManager
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "SyncWorker"
        /** Input data key: true for initial 100-record pull, false for delta sync */
        const val KEY_IS_INITIAL_SYNC = "is_initial_sync"
        /** Number of pages to fetch during initial sync (10 pages x 10 records = 100 records) */
        private const val INITIAL_SYNC_PAGES = 10
        private const val PAGE_SIZE = 10
    }

    /**
     * Main entry point called by WorkManager.
     * Executes the 3-phase sync: push local changes, pull server changes, cleanup tombstones.
     * @return [Result.success] on completion, [Result.retry] on transient failures (up to 3 attempts),
     *         [Result.failure] when auth is missing or retries are exhausted
     */
    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting sync work...")

        val token = sessionManager.getAuthToken()
        val userId = sessionManager.getUserId()

        if (token.isNullOrBlank() || userId == -1L) {
            Log.w(TAG, "No auth token or userId, skipping sync")
            return Result.failure()
        }

        val bearerToken = if (token.startsWith("Bearer ")) token else "Bearer $token"
        val isInitialSync = inputData.getBoolean(KEY_IS_INITIAL_SYNC, false)

        return try {
            // Phase 1: PUSH local changes to server
            pushLocalChanges(userId, bearerToken)

            // Phase 2: PULL server changes
            if (isInitialSync) {
                pullInitialSync(userId, bearerToken)
            } else {
                pullDeltaSync(userId, bearerToken)
            }

            // Phase 3: CLEANUP tombstones
            dao.cleanupSyncedTombstones()

            Log.d(TAG, "Sync completed successfully")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)

            // Retry if transient error, fail if permanent
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    /**
     * Push all unsynced local records to the server.
     * Handles both new records and soft-deleted records.
     */
    private suspend fun pushLocalChanges(userId: Long, token: String) {
        val unsyncedEntries = dao.getUnsyncedWeights()

        if (unsyncedEntries.isEmpty()) {
            Log.d(TAG, "No local changes to push")
            return
        }

        Log.d(TAG, "Pushing ${unsyncedEntries.size} local changes")

        for (entry in unsyncedEntries) {
            try {
                if (entry.isDeleted) {
                    // Push deletion
                    val response = api.deleteWeight(entry.id, token)
                    if (response.isSuccessful || response.code() == 404) {
                        // 404 means already deleted on server, which is fine
                        dao.hardDeleteWeight(entry.id)
                        Log.d(TAG, "Deleted entry ${entry.id}")
                    }
                } else {
                    // Push new/updated weight (include entry ID for idempotent retry)
                    val request = WeightLogRequest(
                        id = entry.id,
                        weight = entry.weight,
                        date = entry.date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    )
                    val response = api.addWeight(userId, token, request)

                    if (response.isSuccessful && response.body() != null) {
                        val serverEntry = response.body()!!
                        // Update local record with server's ID and timestamps
                        if (serverEntry.id != entry.id) {
                            // Server assigned a different ID, replace local record
                            dao.hardDeleteWeight(entry.id)
                        }
                        dao.insertWeight(serverEntry.toEntity(userId))
                        Log.d(TAG, "Synced entry ${serverEntry.id}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to push entry ${entry.id}", e)
                // Continue with other entries, this one will be retried next sync
            }
        }
    }

    /**
     * Initial sync: Fetch most recent 100 records using existing pagination endpoint.
     * Uses 10 pages x 10 records per page = 100 records total.
     * Progressive loading allows partial success.
     */
    private suspend fun pullInitialSync(userId: Long, token: String) {
        Log.d(TAG, "Performing initial sync (${INITIAL_SYNC_PAGES} pages)")

        var totalSynced = 0

        for (page in 0 until INITIAL_SYNC_PAGES) {
            try {
                val response = api.getUserHistory(userId, token, page, PAGE_SIZE)

                if (response.isSuccessful && response.body() != null) {
                    val pageData = response.body()!!
                    val entities = pageData.content.map { it.toEntity(userId) }

                    if (entities.isNotEmpty()) {
                        dao.insertAll(entities)
                        totalSynced += entities.size
                        Log.d(TAG, "Initial sync page $page: ${entities.size} entries")
                    }

                    // Stop if this is the last page
                    if (pageData.last || pageData.content.isEmpty()) {
                        Log.d(TAG, "Reached last page at $page")
                        break
                    }
                } else {
                    Log.w(TAG, "Initial sync page $page failed: ${response.code()}")
                    break
                }
            } catch (e: Exception) {
                Log.e(TAG, "Initial sync page $page error", e)
                // Continue to save whatever we got
                break
            }
        }

        // Store current time as last sync timestamp
        sessionManager.saveLastSyncTimestamp(
            java.time.LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )

        Log.d(TAG, "Initial sync complete: $totalSynced total entries")
    }

    /**
     * Delta sync: Fetch only changes since last sync.
     * Uses the delta sync endpoint which returns records modified after timestamp.
     */
    private suspend fun pullDeltaSync(userId: Long, token: String) {
        val lastSync = sessionManager.getLastSyncTimestamp()

        if (lastSync == null) {
            // No previous sync, fall back to initial sync
            Log.d(TAG, "No last sync timestamp, performing initial sync instead")
            pullInitialSync(userId, token)
            return
        }

        Log.d(TAG, "Performing delta sync since: $lastSync")

        var hasMore = true
        var pageCount = 0
        var currentSince: String = lastSync
        var totalSynced = 0

        while (hasMore && pageCount < 10) { // Safety limit
            try {
                val response = api.deltaSync(userId, token, currentSince, 50)

                if (response.isSuccessful && response.body() != null) {
                    val syncResponse = response.body()!!

                    for (entry in syncResponse.entries) {
                        val entity = entry.toEntity(userId)

                        if (entity.isDeleted) {
                            // Server says delete - hard delete locally
                            dao.hardDeleteWeight(entity.id)
                            Log.d(TAG, "Deleted local entry ${entity.id}")
                        } else {
                            // Upsert the record
                            dao.insertWeight(entity)
                        }
                        totalSynced++
                    }

                    hasMore = syncResponse.hasMore

                    // Update since param to last entry's updatedAt for pagination
                    if (syncResponse.entries.isNotEmpty()) {
                        currentSince = syncResponse.entries.last().updatedAt ?: currentSince
                    }

                    // Save server timestamp for next sync
                    sessionManager.saveLastSyncTimestamp(syncResponse.serverTimestamp)
                    pageCount++

                    Log.d(TAG, "Delta sync page $pageCount: ${syncResponse.entries.size} entries, hasMore=$hasMore")
                } else {
                    Log.w(TAG, "Delta sync failed: ${response.code()}")
                    break
                }
            } catch (e: Exception) {
                Log.e(TAG, "Delta sync error", e)
                break
            }
        }

        Log.d(TAG, "Delta sync complete: $totalSynced total changes")
    }
}
