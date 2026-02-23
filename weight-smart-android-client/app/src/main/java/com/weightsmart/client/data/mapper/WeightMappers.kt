package com.weightsmart.client.data.mappers

import com.weightsmart.client.data.local.entity.WeightEntryEntity
import com.weightsmart.client.data.remote.dto.WeightEntryResponse
import java.time.LocalDateTime

/**
 * WeightMappers.kt
 * Extension functions for mapping weight-related DTOs to Room entities.
 *
 * Architecture Role:
 * Bridges the Data layer (network DTOs) and the Local layer (Room entities).
 * Centralizes the conversion logic so that repositories do not need to know
 * the internal structure of either DTOs or entities. Handles sync-specific
 * field mapping (timestamps, deleted flags, synced status).
 *
 * @author James Chase
 * @version 1.1
 * @since 2026-01-20
 */

/**
 * Maps a server WeightEntryResponse DTO to a local WeightEntryEntity.
 *
 * Conversion details:
 * - Parses ISO-8601 date strings into LocalDateTime.
 * - Falls back to LocalDateTime.now() if createdAt/updatedAt are null.
 * - Marks the entity as isSynced=true (it came from the server).
 * - Preserves the server's isDeleted flag (defaults to false if null).
 *
 * @param userId The owning user's ID (not included in the server response).
 * @return A WeightEntryEntity ready for Room insertion.
 */
fun WeightEntryResponse.toEntity(userId: Long): WeightEntryEntity {
    val now = LocalDateTime.now()
    return WeightEntryEntity(
        id = this.id,
        userId = userId,
        weight = this.weight,
        date = LocalDateTime.parse(this.date), // Assumes ISO-8601 string from server

        // Sync timestamps from server (fallback to now if not provided)
        createdAt = this.createdAt?.let { LocalDateTime.parse(it) } ?: now,
        updatedAt = this.updatedAt?.let { LocalDateTime.parse(it) } ?: now,

        // SYNC LOGIC: Since this came from the server, it is "Synced"
        isSynced = true,

        // Respect Server's Deleted Status (Safe fallback to false)
        isDeleted = this.isDeleted ?: false
    )
}