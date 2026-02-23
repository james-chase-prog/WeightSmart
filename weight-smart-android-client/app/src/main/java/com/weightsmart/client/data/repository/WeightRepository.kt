package com.weightsmart.client.data.repository

import android.util.Log
import com.weightsmart.client.data.SessionManager
import com.weightsmart.client.data.local.WeightDao
import com.weightsmart.client.data.local.entity.WeightEntryEntity
import com.weightsmart.client.data.mappers.toEntity
import com.weightsmart.client.data.remote.WeightSmartApi
import com.weightsmart.client.data.remote.dto.WeightLogRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WeightRepository"

/**
 * WeightRepository
 * Mediates between the remote API and the local Room database for weight entries.
 *
 * Architecture Role:
 * Implements the offline-first strategy: writes to Room first, then attempts to sync
 * with the server. If the network call fails, the entry remains in Room with isSynced=false
 * and will be pushed by the SyncWorker on the next background sync cycle.
 *
 * Key Concepts & Documentation:
 * Offline-First: Local database is the single source of truth; server syncs asynchronously.
 * <a href="https://developer.android.com/topic/architecture/data-layer/offline-first">Reference: Offline-first</a>
 *
 * @author James Chase
 * @version 2.0 (P5: suspend SessionManager API)
 * @since 2026-01-20
 */
@Singleton
class WeightRepository @Inject constructor(
    private val api: WeightSmartApi,
    private val dao: WeightDao,
    private val sessionManager: SessionManager
) {

    // --- VIEW ---

    /**
     * Observes all non-deleted weight entries for the current user.
     * @return A Flow that emits the full list whenever the weight_entries table changes.
     */
    suspend fun getAllWeights(): Flow<List<WeightEntryEntity>> {
        val userId = sessionManager.getUserId()
        if (userId == -1L) return emptyFlow()
        return dao.getWeightsForUser(userId)
    }

    // --- OBSERVE CURRENT WEIGHT ---
    /**
     * Observes the most recent weight entry from Room.
     * Emits automatically whenever the weight_entries table changes
     * (manual entry, sync pull, delete). Single source of truth for current weight display.
     */
    suspend fun observeMostRecentWeight(): Flow<Double?> {
        val userId = sessionManager.getUserId()
        if (userId == -1L) return flowOf(null)
        return dao.getMostRecentWeight(userId).map { it?.weight }
    }

    // --- ADD ---

    /**
     * Logs a new weight entry using the offline-first strategy.
     * Inserts into Room immediately (with a temporary UUID), then attempts to POST to
     * the server. On server success, replaces the temp ID with the server-assigned ID.
     *
     * @param weight The weight value in the user's preferred unit.
     * @param date The date and time the weight was recorded.
     * @return Result.success even on network failure (entry is queued for SyncWorker).
     */
    suspend fun addWeight(weight: Double, date: LocalDateTime): Result<Unit> {
        val userId = sessionManager.getUserId()
        Log.d(TAG, "addWeight called: userId=$userId, weight=$weight, date=$date")

        if (userId == -1L) {
            Log.e(TAG, "addWeight failed: No valid userId in session")
            return Result.failure(Exception("No valid user session"))
        }

        val tempId = UUID.randomUUID().toString()
        val tempEntry = WeightEntryEntity(
            id = tempId,
            userId = userId,
            weight = weight,
            date = date,
            isSynced = false,
            isDeleted = false
        )

        try {
            dao.insertWeight(tempEntry) // Save Local first (offline-first)
            Log.d(TAG, "Weight saved locally with tempId=$tempId")

            // Update in-memory User so UI reflects new current weight immediately
            sessionManager.updateCurrentWeight(weight)

            val token = getAuthToken()
            if (token.isEmpty()) {
                Log.w(TAG, "No auth token - weight queued for sync")
                return Result.success(Unit) // Will be synced later by SyncWorker
            }

            val request = WeightLogRequest(
                id = tempId,
                weight = weight,
                date = date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )

            Log.d(TAG, "Sending weight to server: POST /api/weights/$userId")
            val response = api.addWeight(userId, token, request)

            if (response.isSuccessful && response.body() != null) {
                val serverEntry = response.body()!!
                Log.d(TAG, "Server accepted weight with id=${serverEntry.id}")

                // Swap temp ID with server-assigned ID
                if (serverEntry.id != tempId) {
                    dao.hardDeleteWeight(tempId)
                }

                dao.insertWeight(serverEntry.toEntity(userId))
                Log.d(TAG, "Local DB updated with server entry")
            } else {
                Log.e(TAG, "Server rejected weight: ${response.code()} - ${response.message()}")
                // Weight stays in local DB with isSynced=false, SyncWorker will retry
            }

            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "addWeight exception: ${e.message}", e)
            // Weight is already in local DB, SyncWorker will retry
            return Result.success(Unit)
        }
    }

    // --- DELETE ---

    /**
     * Soft-deletes a weight entry locally, then attempts to notify the server.
     * On network failure the entry remains soft-deleted locally; SyncWorker will retry.
     *
     * @param weightId The UUID of the weight entry to delete.
     * @return Result.success in all cases (offline-first guarantee).
     */
    suspend fun deleteWeight(weightId: String): Result<Unit> {
        try {
            dao.softDeleteWeight(weightId) // Save Local

            val token = getAuthToken()
            if (token.isNotEmpty()) {
                api.deleteWeight(weightId, token)
            }
            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.success(Unit)
        }
    }

    // --- FIRST WEIGHT (Goal Direction Inference) ---
    /**
     * Fetches the user's earliest recorded weight value.
     * Tries the server first (source of truth), falls back to local DB if offline.
     * Returns null if the user has no weight entries at all.
     */
    suspend fun getFirstWeight(): Double? {
        val userId = sessionManager.getUserId()
        if (userId == -1L) return null

        // Try server first: page=0, size=1, sort=date,asc -> earliest entry
        try {
            val token = getAuthToken()
            if (token.isNotEmpty()) {
                val response = api.getUserHistory(userId, token, page = 0, size = 1, sort = "date,asc")
                if (response.isSuccessful) {
                    val first = response.body()?.content?.firstOrNull()
                    if (first != null) return first.weight
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Server fetch for first weight failed, falling back to local: ${e.message}")
        }

        // Offline fallback: query local Room DB
        return dao.getOldestWeight(userId)?.weight
    }

    // --- PRE-LOGOUT FLUSH ---

    /**
     * Best-effort push of all unsynced local changes to the server.
     * Called before logout to prevent data loss — especially soft-deleted entries
     * that only exist locally with isSynced=0. If the network is unavailable,
     * changes are silently lost (the user explicitly chose to log out).
     *
     * Mirrors SyncWorker.pushLocalChanges() but runs synchronously in the
     * logout coroutine rather than via WorkManager.
     */
    suspend fun pushPendingChanges() {
        try {
            val userId = sessionManager.getUserId()
            val token = getAuthToken()
            if (userId == -1L || token.isEmpty()) return

            val unsyncedEntries = dao.getUnsyncedWeights()
            if (unsyncedEntries.isEmpty()) return

            Log.d(TAG, "Pre-logout flush: pushing ${unsyncedEntries.size} pending changes")

            for (entry in unsyncedEntries) {
                try {
                    if (entry.isDeleted) {
                        val response = api.deleteWeight(entry.id, token)
                        if (response.isSuccessful || response.code() == 404) {
                            dao.hardDeleteWeight(entry.id)
                            Log.d(TAG, "Pre-logout: deleted ${entry.id}")
                        }
                    } else {
                        val request = WeightLogRequest(
                            id = entry.id,
                            weight = entry.weight,
                            date = entry.date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        )
                        val response = api.addWeight(userId, token, request)
                        if (response.isSuccessful && response.body() != null) {
                            val serverEntry = response.body()!!
                            if (serverEntry.id != entry.id) {
                                dao.hardDeleteWeight(entry.id)
                            }
                            dao.insertWeight(serverEntry.toEntity(userId))
                            Log.d(TAG, "Pre-logout: synced ${serverEntry.id}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Pre-logout: failed to push ${entry.id}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pre-logout flush failed: ${e.message}")
        }
    }

    /**
     * Retrieves the JWT token from SessionManager and ensures it has the "Bearer " prefix.
     * @return The prefixed token string, or empty string if no token exists.
     */
    private suspend fun getAuthToken(): String {
        val token = sessionManager.getAuthToken() ?: ""
        if (token.isBlank()) return ""
        return if (token.startsWith("Bearer ")) token else "Bearer $token"
    }
}
