package com.example.weightsmart.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room persistence shape for users.
 * Note: role is stored as String to avoid needing a TypeConverter.
 */
@Entity(
    tableName = "users",
    indices = [Index(value = ["username"], unique = true)]
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,

    // Identity
    val username: String,
    val email: String,

    // Auth (KDF only; never store plaintext)
    val passwordHashB64: String,
    val saltB64: String,
    val iterations: Int,

    // Authorization
    val role: String = "USER", // map to/from core.auth.Role in repo/facade

    // Profile snapshot
    val nickname: String? = null,
    val currentWeight: Double? = null,          // one decimal; latest entry
    val currentWeightEpochSec: Long? = null,    // when currentWeight was measured
    val phoneNumber: String? = null,
    val smsConsent: Boolean? = null,
    val avatar: String = "avatar_default",

    // Metadata
    val createdAtEpochSec: Long = System.currentTimeMillis() / 1000
)
