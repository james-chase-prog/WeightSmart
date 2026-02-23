package com.weightsmart.client.data.remote.dto

/**
 * DeltaSyncResponse
 * DTO returned by GET /api/weights/{userId}/sync?since={timestamp}.
 * Matches server: com.weightsmart.server.dto.DeltaSyncResponse
 *
 * Architecture Role:
 * Carries the payload for incremental (delta) synchronization. After initial sync,
 * the SyncWorker uses this endpoint to pull only records modified since the last
 * successful sync, reducing bandwidth and battery usage.
 *
 * The serverTimestamp should be stored locally (via SessionManager.saveLastSyncTimestamp)
 * and used as the 'since' parameter for the next sync request.
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-02-04
 */
data class DeltaSyncResponse(
    /**
     * List of weight entries modified since the 'since' parameter.
     * Includes both active records and tombstones (isDeleted=true).
     */
    val entries: List<WeightEntryResponse>,

    /**
     * Server timestamp at the time of this response.
     * Store this locally and use as 'since' for next sync.
     * Format: ISO-8601 string (e.g., "2026-02-04T10:30:00")
     */
    val serverTimestamp: String,

    /**
     * If true, more pages of changes exist.
     * Use the last entry's updatedAt as the new 'since' to continue.
     */
    val hasMore: Boolean
)
