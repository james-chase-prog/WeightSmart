package com.weightsmart.server.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/*
 * DeltaSyncResponse
 * Container for delta synchronization results.
 *
 * Architecture Role:
 * Wraps weight entries with sync metadata to enable efficient offline-first synchronization.
 * The 'serverTimestamp' field allows clients to store the "high watermark" for the next sync,
 * ensuring they only request changes since their last successful sync.
 *
 * Key Concepts:
 * Delta Sync: Only transfers records modified since the client's last sync timestamp.
 * Tombstones: Soft-deleted records (isDeleted=true) are included so clients can remove them locally.
 * Pagination: 'hasMore' indicates if additional pages of changes exist.
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-02-04
 */
@Data
@Builder
public class DeltaSyncResponse {

    /**
     * List of weight entries modified since the 'since' parameter.
     * Includes both active records and tombstones (isDeleted=true).
     * Ordered by updatedAt ascending for consistent pagination.
     */
    private List<WeightEntryResponse> entries;

    /**
     * Current server timestamp at the time of this response.
     * Client should store this and use it as the 'since' parameter for the next sync.
     * This ensures no changes are missed between syncs.
     */
    private LocalDateTime serverTimestamp;

    /**
     * Pagination indicator.
     * If true, client should make another request with the last entry's updatedAt
     * as the new 'since' parameter to fetch remaining changes.
     */
    private boolean hasMore;
}
