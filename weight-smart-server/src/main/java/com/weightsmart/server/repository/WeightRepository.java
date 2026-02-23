package com.weightsmart.server.repository;

import com.weightsmart.server.model.User;
import com.weightsmart.server.model.WeightEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/*
 * WeightRepository
 * Handles Data Access Object (DAO) operations for WeightEntry entities.
 *
 * Architecture Role:
 * Supports the three core data views: Infinite Scroll (History), Graphs (Time-Bounded), and Status (Latest).
 *
 * Key Concepts & Documentation:
 * <a href="https://docs.spring.io/spring-data/commons/docs/current/api/org/springframework/data/domain/Page.html">Spring Data Page</a>:
 * The return type used for pagination. Unlike Slice, Page provides total record counts, enabling "Page 1 of 5" footers for the Web Client.
 * <a href="https://docs.spring.io/spring-data/jpa/reference/repositories/query-methods-details.html">Query Method Keywords</a>:
 * Documentation for keyword parsing (e.g., "OrderBy", "Between", "Top").
 * </ul>
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-01-14
 */

/**
 * Repository interface for WeightEntry operations.
 * Handles data retrieval for logs (scrolling), graphs (time-bounded), and current status.
 */
@Repository
public interface WeightRepository extends JpaRepository<WeightEntry, UUID> {

    /**
     * Retrieves a PAGINATED list of weight entries ordered by newest first.
     * Returns total count and page metadata useful for Web and Android pagination.
     *
     * @param user     The User entity whose records should be retrieved.
     * @param pageable Defines the limit (page size) and offset (page number).
     * @return A Page of WeightEntry objects. Includes total pages and total elements count.
     * <p>
     * Generated SQL 1: SELECT * FROM weight_entries WHERE user_id = ? ORDER BY date DESC LIMIT ? OFFSET ?
     * Generated SQL 2: SELECT COUNT(id) FROM weight_entries WHERE user_id = ?
     */
    Page<WeightEntry> findByUserOrderByDateDesc(User user, Pageable pageable);

    /**
     * Retrieves all weight entries for a user within a specific date range, ordered chronologically.
     * This method is designed for generating graphs and analytics where all data points
     * in the range are required at once (no pagination).
     * ARCHITECTURAL NOTE:
     * This is to be called exclusively on server-side operations
     * Adhering to this rule avoids sending 1000's of records to the client side
     * Server side logic will determine the number of values to send client side when this method is called
     * This method fetches RAW data. It is intended for:
     *   1. Short-range graphs (e.g., "Last 30 Days") where full detail is needed.
     *   2. Server-side calculations (e.g., "Calculate Average Weight").
     *   CAUTION: Do not expose this directly to the client for long date ranges
     *   without downsampling (aggregation) in the Service layer first.
     *
     * @param user      The User entity.
     * @param startDate The beginning of the range (inclusive).
     * @param endDate   The end of the range (inclusive).
     * @return A complete List of WeightEntry objects within the range.
     *
     * Generated SQL: SELECT * FROM weight_entries WHERE user_id = ? AND date BETWEEN ? AND ? ORDER BY date ASC
     */
    List<WeightEntry> findByUserAndDateBetweenOrderByDateAsc(User user, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Retrieves the single most recent NON-DELETED weight entry for a user.
     * Used for currentWeight cache recalculation after adds, deletes, and back-dated entries.
     * Filters out soft-deleted tombstones to ensure the cache reflects active data only.
     *
     * @param user The User entity.
     * @return An Optional containing the latest active WeightEntry, or empty if no active entries exist.
     *
     * Generated SQL: SELECT * FROM weight_entries WHERE user_id = ? AND is_deleted = false ORDER BY date DESC LIMIT 1
     */
    Optional<WeightEntry> findTopByUserAndIsDeletedFalseOrderByDateDesc(User user);

    /**
     * Counts non-deleted weight entries within a date range.
     * Used by WeightService.getGraphData() to determine whether downsampling is needed
     * without loading all records into memory.
     *
     * @param user      The User entity.
     * @param startDate The beginning of the range (inclusive).
     * @param endDate   The end of the range (inclusive).
     * @return The number of active weight entries in the range.
     *
     * Generated SQL: SELECT COUNT(*) FROM weight_entries WHERE user_id = ? AND is_deleted = false AND date BETWEEN ? AND ?
     */
    long countByUserAndIsDeletedFalseAndDateBetween(User user, LocalDateTime startDate, LocalDateTime endDate);

    // --- DELTA SYNC (P0: Offline-First Synchronization) ---

    /**
     * Retrieves weight entries modified after a specific timestamp.
     * Used by the Android sync endpoint to fetch only changed records.
     *
     * ARCHITECTURAL NOTE:
     * This query INCLUDES soft-deleted records (tombstones) so the client
     * knows to remove them from local storage. The 'isDeleted' field in
     * the response tells the client whether to upsert or delete locally.
     *
     * @param user     The User entity.
     * @param since    The timestamp from client's last successful sync (exclusive).
     * @param pageable Pagination info (recommended size: 50 for mobile).
     * @return A Slice of WeightEntry objects modified after 'since', ordered by updatedAt.
     *         Slice skips the COUNT query (unlike Page), since sync only needs hasNext.
     *
     * Generated SQL: SELECT * FROM weight_entries
     *                WHERE user_id = ? AND updated_at > ?
     *                ORDER BY updated_at ASC
     *                LIMIT ? OFFSET ?
     */
    Slice<WeightEntry> findByUserAndUpdatedAtAfterOrderByUpdatedAtAsc(User user, LocalDateTime since, Pageable pageable);
}