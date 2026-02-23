package com.weightsmart.client.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import java.util.UUID

/**
 * WeightEntryEntity
 * Room entity representing a single weight log entry in the local SQLite database.
 *
 * Architecture Role:
 * This is the local persistence model for weight data. Its schema mirrors the server's
 * WeightEntry JPA entity, with an additional Android-specific field (isSynced) to track
 * offline-first sync status. Room stores LocalDateTime fields as epoch milliseconds
 * via the Converters class in WeightDatabase.kt.
 *
 * Key Concepts & Documentation:
 * Room Entity: Maps this data class to a SQLite table row.
 * <a href="https://developer.android.com/training/data-storage/room/defining-data">Reference: Room Entities</a>
 *
 * @property id UUID primary key, defaults to a random UUID for offline-created entries.
 *   Replaced with the server-assigned ID after successful sync.
 * @property userId Foreign key linking this entry to the owning user.
 * @property weight The recorded weight value.
 * @property date The date and time when the weight was recorded.
 * @property createdAt Timestamp of initial creation (mirrors server's @CreationTimestamp).
 * @property updatedAt Timestamp of last modification (mirrors server's @UpdateTimestamp).
 *   Used as the cursor for delta sync.
 * @property isDeleted Soft-delete flag. When true, this row is a tombstone awaiting server acknowledgment.
 * @property isSynced Android-specific flag. False means this entry has local changes not yet pushed to the server.
 *
 * @author James Chase
 * @version 1.1
 * @since 2026-01-20
 */
@Entity(tableName = "weight_entries")
data class WeightEntryEntity(
    // Matches Server: @Id private UUID id;
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    // Matches Server: @ManyToOne private User user;
    val userId: Long,

    // Matches Server: @Column private Double weight;
    val weight: Double,

    // Matches Server: @Column(name = "date_recorded") private LocalDateTime date;
    val date: LocalDateTime,

    // --- SYNC FIELDS (Matches Server Logic) ---

    // Matches Server: @CreationTimestamp private LocalDateTime createdAt;
    val createdAt: LocalDateTime = LocalDateTime.now(),

    // Matches Server: @UpdateTimestamp private LocalDateTime updatedAt;
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    // Matches Server: private Boolean isDeleted = false;
    val isDeleted: Boolean = false,

    // --- ANDROID SPECIFIC ---
    // (Not on server, but needed locally to know what to upload)
    val isSynced: Boolean = false
)