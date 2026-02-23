package com.weightsmart.server.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

/*
 * WeightEntryResponse DTO
 * Safe container for sending weight log data to the client.
 *
 * Architecture Role:
 * Strips the heavy User relationship from the WeightEntry entity to prevent:
 * 1. Infinite JSON recursion (User -> WeightEntries -> User -> ...).
 * 2. Bandwidth waste (User object adds ~500 bytes per entry).
 * 3. Data leakage (password hashes, lock timers exposed in nested objects).
 *
 * Key Concepts & Documentation:
 * Builder Pattern: Lombok's @Builder generates a fluent API for constructing response objects in mappers.
 * <a href="https://projectlombok.org/features/Builder">Reference: Lombok @Builder</a>
 * UUID: 128-bit Universally Unique Identifiers prevent sequential ID enumeration attacks.
 * <a href="https://docs.oracle.com/javase/8/docs/api/java/util/UUID.html">Reference: Java UUID</a>
 *
 * @author James Chase
 * @version 1.1
 * @since 2026-01-14
 */
@Data
@Builder
public class WeightEntryResponse {

    // Unique entry identifier (UUID). Prevents enumeration attacks vs sequential Long ID
    private UUID id;

    // Weight value in pounds (e.g., 184.6).
    private Double weight;

    // Timestamp of the weight measurement in ISO-8601 format
    private LocalDateTime date;

    // Soft deletion flag. True = tombstone (client should remove from local storage)
    private Boolean isDeleted;

    // --- SYNC FIELDS (P0: Offline-First Sync) ---

    // Server-side creation timestamp. Set by Hibernate @CreationTimestamp on first INSERT.
    private LocalDateTime createdAt;

    // Server-side modification timestamp. Drives delta sync (WHERE updatedAt > lastSync)
    private LocalDateTime updatedAt;

    // intentionally exclude 'User' to save bandwidth and prevent recursion
}