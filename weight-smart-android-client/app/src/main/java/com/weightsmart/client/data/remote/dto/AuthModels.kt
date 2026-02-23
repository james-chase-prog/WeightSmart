package com.weightsmart.client.data.remote.dto

/**
 * AuthModels.kt
 * Data Transfer Objects (DTOs) for authentication and user profile endpoints.
 *
 * Architecture Role:
 * These classes are the client-side mirrors of the Spring Boot server DTOs
 * (com.weightsmart.server.dto.*). GSON serializes/deserializes them automatically
 * during Retrofit HTTP calls. They live in the Data layer and are mapped to
 * Domain models (User) via extension functions in UserMappers.kt.
 *
 * Key Concepts & Documentation:
 * Data Class: Kotlin class that auto-generates equals(), hashCode(), toString(), copy().
 * <a href="https://kotlinlang.org/docs/data-classes.html">Reference: Kotlin Data Classes</a>
 * GSON: Google's JSON serialization library used by Retrofit's GsonConverterFactory.
 * <a href="https://github.com/google/gson">Reference: GSON</a>
 *
 * @author James Chase
 * @version 1.1 (P5: AuthResponse gained refreshToken field for JWT refresh flow)
 * @since 2026-01-20
 */

/**
 * LoginRequest
 * Sent to POST /api/auth/login to authenticate an existing user.
 * Matches server: LoginRequest.java
 *
 * @property username The user's unique identifier.
 * @property password The user's plaintext password (hashed server-side).
 */
data class LoginRequest(
    val username: String,
    val password: String
)

/**
 * RegisterRequest
 * Sent to POST /api/auth/register to create a new account.
 * Matches server: RegisterRequest.java
 *
 * Note: Dates are sent as Strings ("YYYY-MM-DD") to avoid GSON parsing errors
 * with Java 8 date types.
 *
 * @property username Unique identifier (validated for availability via Trie search).
 * @property email Unique contact email.
 * @property password Plaintext password (hashed server-side with BCrypt).
 * @property nickname Optional display name shown in the UI.
 * @property age User's age (server enforces minimum of 13).
 * @property height Optional height in the user's preferred unit.
 * @property currentWeight Optional starting weight at registration.
 * @property goalWeight Optional target weight for goal tracking.
 * @property targetDate Optional goal deadline as ISO date string ("2026-06-01").
 */
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val nickname: String?,
    val age: Int,
    val height: Double?,
    val currentWeight: Double?,
    val goalWeight: Double?,
    val targetDate: String?
)

/**
 * AuthResponse
 * Returned by both login and register endpoints on success.
 * Matches server: AuthResponse.java
 *
 * @property token JWT access token for authenticating subsequent requests.
 * @property refreshToken Long-lived refresh token for obtaining new access tokens (P5).
 * @property user The user's full profile (safe view, no password hash).
 */
data class AuthResponse(
    val token: String,
    val refreshToken: String,
    val user: UserDto
)

/**
 * UserDto
 * The "safe view" of a user profile returned by the server (excludes password hash).
 * Matches server: UserResponse.java
 *
 * @property id Server-assigned primary key.
 * @property username Unique login identifier.
 * @property email Contact email address.
 * @property nickname Optional display name.
 * @property age User's age.
 * @property height Optional height value.
 * @property currentWeight Most recent weight as stored on the server profile.
 * @property goalWeight Target weight for goal tracking.
 * @property targetDate Goal deadline as ISO date string, or null.
 * @property role Authorization role (e.g., "ROLE_USER", "ROLE_ADMIN").
 */
data class UserDto(
    val id: Long,
    val username: String,
    val email: String,
    val nickname: String?,
    val age: Int,
    val height: Double?,
    val currentWeight: Double?,
    val goalWeight: Double?,
    val targetDate: String?,
    val role: String
)

/**
 * UpdateProfileRequest
 * Sent to PUT /api/auth/update for non-sensitive profile field changes.
 * Matches server: UpdateProfileRequest.java
 *
 * Note: All fields are nullable; only non-null fields are updated server-side.
 * Uses String for targetDate to match the JSON format ("YYYY-MM-DD").
 *
 * @property age Updated age, or null to leave unchanged.
 * @property height Updated height, or null to leave unchanged.
 * @property nickname Updated display name, or null to leave unchanged.
 * @property goalWeight Updated target weight, or null to leave unchanged.
 * @property targetDate Updated goal deadline as ISO date string, or null to leave unchanged.
 */
data class UpdateProfileRequest(
    val age: Int?,
    val height: Double?,
    val nickname: String?,
    val goalWeight: Double?,
    val targetDate: String?
)