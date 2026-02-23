package com.weightsmart.client.domain.repository

import com.weightsmart.client.domain.model.User

/**
 * AuthRepository
 * The Domain layer contract for authentication operations.
 *
 * Architecture Role:
 * Defines *WHAT* the app can do regarding auth (login, register, check status)
 * without defining *HOW* it is done. This allows the Domain layer (ViewModels)
 * to be pure Kotlin code, independent of Android, Retrofit, or Room APIs.
 * The concrete implementation ([AuthRepoCancsitoryImpl]) is bound via Hilt DI.
 *
 * Key Concepts & Documentation:
 * Dependency Inversion: High-level modules (ViewModels) should not depend on
 * low-level modules (RepositoryImpl). Both should depend on abstractions (this interface).
 * <a href="https://en.wikipedia.org/wiki/Dependency_inversion_principle">Reference: Dependency Inversion Principle</a>
 *
 * @author James Chase
 * @version 1.2 (P5: isLoggedIn + getCurrentUser changed to suspend for DataStore migration)
 * @since 2026-01-20
 */
interface AuthRepository {

    /**
     * Checks if a valid session currently exists.
     * @return true if token AND user data are present locally.
     */
    suspend fun isLoggedIn(): Boolean

    /**
     * Returns the currently stored user from the session.
     * @return User if valid session exists, null otherwise.
     */
    suspend fun getCurrentUser(): User?

    /**
     * Authenticates an existing user via the remote API.
     * @param username The user's unique identifier.
     * @param password The user's password as a CharArray (zeroed after use for security).
     * @return Result containing the authenticated User profile, or a failure Exception.
     */
    suspend fun login(username: String, password: CharArray): Result<User>

    /**
     * Creates a new user account and performs the initial login in a single flow.
     * @param username Unique identifier (checked for availability via Trie search).
     * @param email Unique contact email.
     * @param password Raw password as CharArray (hashed server-side with BCrypt).
     * @param age User's age (server enforces minimum of 13).
     * @param nickname Optional display name.
     * @param height Optional height value.
     * @param currentWeight Optional starting weight.
     * @param goalWeight Optional target weight.
     * @param targetDate Optional goal deadline as ISO date string ("YYYY-MM-DD").
     * @return Result containing the new User profile, or a failure Exception.
     */
    suspend fun register(
        username: String,
        email: String,
        password: CharArray,
        age: Int,
        nickname: String?,
        height: Double?,
        currentWeight: Double?,
        goalWeight: Double?,
        targetDate: String?
    ): Result<User>

    /**
     * Updates non-sensitive profile fields on the server.
     * All fields are nullable; only non-null values are updated server-side.
     *
     * @param nickname Updated display name, or null to leave unchanged.
     * @param age Updated age, or null to leave unchanged.
     * @param height Updated height, or null to leave unchanged.
     * @param goalWeight Updated target weight, or null to leave unchanged.
     * @param targetDate Updated goal deadline as ISO date string ("YYYY-MM-DD"), or null.
     * @return Result containing the updated User profile from the server, or a failure Exception.
     */
    suspend fun updateProfile(
        nickname: String?,
        age: Int?,
        height: Double?,
        goalWeight: Double?,
        targetDate: String?
    ): Result<User>

    /**
     * Searches for available usernames matching the prefix.
     * Used for the "Search As You Go" feature.
     *
     * @param prefix The characters typed so far (e.g. "jam").
     * @return A list of matching usernames (e.g. ["James", "Jamie"]).
     */
    suspend fun searchUsernames(prefix: String): Result<List<String>>
}