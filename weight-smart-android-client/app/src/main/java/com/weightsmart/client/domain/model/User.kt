package com.weightsmart.client.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * User
 * Client-side domain model representing the authenticated user's profile.
 *
 * Architecture Role:
 * Pure data container mirroring the server-side User.java entity. Per MVVM,
 * this model holds state only -- business logic belongs in Use Cases and
 * formatting logic in ViewModels or Extension Functions.
 *
 * Key Concepts & Documentation:
 * Data Class: Auto-generates equals(), hashCode(), copy(), and toString().
 * <a href="https://kotlinlang.org/docs/data-classes.html">Reference: Kotlin Data Classes</a>
 * COPPA: Children's Online Privacy Protection Act -- age cannot be null.
 * <a href="https://www.ftc.gov/legal-library/browse/rules/childrens-online-privacy-protection-rule-coppa">Reference: COPPA Rule</a>
 *
 * @property id Server-assigned primary key (matches server User.id)
 * @property username Unique login identifier (immutable after registration)
 * @property email User's email address (used for account recovery)
 * @property age Non-nullable to enforce COPPA compliance checks at registration
 * @property nickname Optional display name shown in UI (editable in Profile)
 * @property height User's height in inches (used for BMI calculations in P8)
 * @property currentWeight Most recent logged weight in lbs (updated on each entry)
 * @property goalWeight Target weight in lbs (drives goal-reached celebrations)
 * @property targetDate Optional deadline for reaching the goal weight
 * @property role RBAC role string from server (read-only on client, no admin controls)
 * @property isEnabled Whether the account is active (server-controlled)
 * @property lockTime Timestamp when account was locked due to failed login attempts
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-01-20
 */
data class User (
    val id: Long,
    val username: String,
    val email: String,

    // --- PROFILE ATTRIBUTES ---
    val age: Int = 0,  // Not nullable to maintain COPPA compliance
    val nickname: String? = null,
    val height: Double? = null,
    val currentWeight: Double? = null,
    val goalWeight: Double? = null,
    val targetDate: LocalDate? = null,

    // --- RBAC & SECURITY (Read-only in Android client, no admin controls) ---
    val role: String = "ROLE_USER",
    val isEnabled: Boolean = true,
    val lockTime: LocalDateTime? = null

    // Never store user password in the client-side model.
    // Password exists only in LoginRequest DTO and is destroyed after authentication.

)

