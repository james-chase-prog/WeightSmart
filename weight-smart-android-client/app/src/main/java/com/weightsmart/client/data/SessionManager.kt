package com.weightsmart.client.data

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.crypto.tink.Aead
import com.weightsmart.client.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SessionManager
 * Manages user session state and profile data.
 *
 * Architecture Role:
 * Local Data Source. Persists the full User profile to Jetpack DataStore with
 * Google Tink (AES-256-GCM) encryption for sensitive values (auth token, email).
 * Non-sensitive values are stored as plaintext DataStore preferences.
 *
 * API:
 * All read/write methods are `suspend` functions (coroutine-native).
 * Consumers already operate in coroutine contexts (ViewModels use viewModelScope,
 * use cases are suspend, repositories are suspend, SyncWorker.doWork() is suspend).
 *
 * Performance Note:
 * Aead is dagger.Lazy-injected to defer Android Keystore initialization from
 * Hilt DI construction (which blocks onCreate) to the first coroutine call
 * (which runs after startup completes).
 *
 * Key Concepts & Documentation:
 * DataStore: Coroutine-native replacement for SharedPreferences.
 * <a href="https://developer.android.com/topic/libraries/architecture/datastore">Reference: Jetpack DataStore</a>
 * Tink Aead: Authenticated Encryption with Associated Data (AES-256-GCM).
 * <a href="https://developers.google.com/tink/aead">Reference: Tink AEAD</a>
 *
 * @author James Chase
 * @version 2.1 (P5: Removed legacy EncryptedSharedPreferences — not deployed)
 * @since 2026-01-20
 */
@Singleton
class SessionManager @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val aead: dagger.Lazy<Aead>
) {
    // RAM cache for quick access during session
    private var currentUser: User? = null

    // Observable user state for reactive UI updates
    private val _userFlow = MutableStateFlow<User?>(null)
    val userFlow: StateFlow<User?> = _userFlow

    companion object {
        // DataStore preference keys
        private val KEY_USER_ID = longPreferencesKey("user_id")
        private val KEY_USERNAME = stringPreferencesKey("user_username")
        private val KEY_EMAIL = stringPreferencesKey("user_email") // encrypted
        private val KEY_AGE = intPreferencesKey("user_age")
        private val KEY_NICKNAME = stringPreferencesKey("user_nickname")
        private val KEY_HEIGHT = doublePreferencesKey("user_height")
        private val KEY_CURRENT_WEIGHT = doublePreferencesKey("user_current_weight")
        private val KEY_GOAL_WEIGHT = doublePreferencesKey("user_goal_weight")
        private val KEY_TARGET_DATE = stringPreferencesKey("user_target_date")
        private val KEY_ROLE = stringPreferencesKey("user_role")
        private val KEY_IS_ENABLED = booleanPreferencesKey("user_is_enabled")
        private val KEY_LOCK_TIME = stringPreferencesKey("user_lock_time")
        private val KEY_AUTH_TOKEN = stringPreferencesKey("auth_token") // encrypted
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token") // encrypted
        private val KEY_LAST_SYNC = stringPreferencesKey("last_sync_timestamp")
        private val KEY_GOAL_REACHED_FOR = floatPreferencesKey("goal_reached_for_weight")
    }

    // --- ENCRYPTION HELPERS ---

    /** Encrypts a string using Tink Aead and returns a Base64-encoded ciphertext. */
    private fun encrypt(plaintext: String): String {
        val ciphertext = aead.get().encrypt(plaintext.toByteArray(Charsets.UTF_8), byteArrayOf())
        return Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    /** Decrypts a Base64-encoded ciphertext using Tink Aead. */
    private fun decrypt(encoded: String): String {
        val ciphertext = Base64.decode(encoded, Base64.NO_WRAP)
        val plaintext = aead.get().decrypt(ciphertext, byteArrayOf())
        return String(plaintext, Charsets.UTF_8)
    }

    // --- USER PERSISTENCE ---

    /**
     * Persists the full user profile to DataStore and updates RAM cache.
     * Sensitive fields (email) are encrypted with Tink before storage.
     */
    suspend fun saveUser(user: User) {
        currentUser = user
        _userFlow.value = user
        dataStore.edit { prefs ->
            prefs[KEY_USER_ID] = user.id
            prefs[KEY_USERNAME] = user.username
            prefs[KEY_EMAIL] = encrypt(user.email)
            prefs[KEY_AGE] = user.age
            user.nickname?.let { prefs[KEY_NICKNAME] = it } ?: prefs.remove(KEY_NICKNAME)
            user.height?.let { prefs[KEY_HEIGHT] = it } ?: prefs.remove(KEY_HEIGHT)
            user.currentWeight?.let { prefs[KEY_CURRENT_WEIGHT] = it } ?: prefs.remove(KEY_CURRENT_WEIGHT)
            user.goalWeight?.let { prefs[KEY_GOAL_WEIGHT] = it } ?: prefs.remove(KEY_GOAL_WEIGHT)
            user.targetDate?.let { prefs[KEY_TARGET_DATE] = it.toString() } ?: prefs.remove(KEY_TARGET_DATE)
            prefs[KEY_ROLE] = user.role
            prefs[KEY_IS_ENABLED] = user.isEnabled
            user.lockTime?.let { prefs[KEY_LOCK_TIME] = it.toString() } ?: prefs.remove(KEY_LOCK_TIME)
        }
    }

    /**
     * Retrieves the user profile.
     * Strategy: RAM cache -> DataStore -> Null
     */
    suspend fun getUser(): User? {
        if (currentUser != null) return currentUser

        val prefs = dataStore.data.first()
        val id = prefs[KEY_USER_ID] ?: return null
        val username = prefs[KEY_USERNAME] ?: return null
        val encryptedEmail = prefs[KEY_EMAIL] ?: return null

        currentUser = User(
            id = id,
            username = username,
            email = decrypt(encryptedEmail),
            age = prefs[KEY_AGE] ?: 0,
            nickname = prefs[KEY_NICKNAME],
            height = prefs[KEY_HEIGHT],
            currentWeight = prefs[KEY_CURRENT_WEIGHT],
            goalWeight = prefs[KEY_GOAL_WEIGHT],
            targetDate = prefs[KEY_TARGET_DATE]?.let { LocalDate.parse(it) },
            role = prefs[KEY_ROLE] ?: "ROLE_USER",
            isEnabled = prefs[KEY_IS_ENABLED] ?: true,
            lockTime = prefs[KEY_LOCK_TIME]?.let { LocalDateTime.parse(it) }
        )
        // Hydrate the flow when loading from disk on app restart
        _userFlow.value = currentUser
        return currentUser
    }

    // --- TOKEN MANAGEMENT ---

    /** Persists the JWT access token (encrypted with Tink). */
    suspend fun saveAuthToken(token: String) {
        dataStore.edit { it[KEY_AUTH_TOKEN] = encrypt(token) }
    }

    /**
     * Retrieves the stored JWT access token.
     * @return The raw token string, or null if no session exists.
     */
    suspend fun getAuthToken(): String? {
        val prefs = dataStore.data.first()
        return prefs[KEY_AUTH_TOKEN]?.let { decrypt(it) }
    }

    /** Persists the JWT refresh token (encrypted with Tink). */
    suspend fun saveRefreshToken(token: String) {
        dataStore.edit { it[KEY_REFRESH_TOKEN] = encrypt(token) }
    }

    /**
     * Retrieves the stored JWT refresh token.
     * @return The raw refresh token string, or null if no session exists.
     */
    suspend fun getRefreshToken(): String? {
        val prefs = dataStore.data.first()
        return prefs[KEY_REFRESH_TOKEN]?.let { decrypt(it) }
    }

    /** Persists the server-assigned user ID. */
    suspend fun saveUserId(userId: Long) {
        dataStore.edit { it[KEY_USER_ID] = userId }
    }

    /**
     * Retrieves the stored user ID.
     * @return The user ID, or -1L if none is stored.
     */
    suspend fun getUserId(): Long {
        val prefs = dataStore.data.first()
        return prefs[KEY_USER_ID] ?: -1L
    }

    /**
     * Checks if user has a valid, non-expired session.
     * Requires a token that hasn't expired AND valid user data (id, username, email).
     * Falls back to checking the refresh token if the access token is expired.
     */
    suspend fun isLoggedIn(): Boolean {
        val token = getAuthToken() ?: return false
        if (getUser() == null || getUserId() == -1L) return false

        // Check if the access token is still valid (not expired)
        if (!isTokenExpired(token)) return true

        // Access token expired — check if refresh token exists and is valid
        val refreshToken = getRefreshToken() ?: return false
        return !isTokenExpired(refreshToken)
    }

    /**
     * Decodes a JWT payload (without signature verification) to check expiration.
     * Signature verification is the server's responsibility — this is a local convenience check
     * to avoid presenting stale sessions to the user.
     *
     * @return true if the token's exp claim is in the past, false if still valid.
     */
    private fun isTokenExpired(token: String): Boolean {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return true
            // Decode the payload (second part) — Base64 URL-safe decoding
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
            // Extract "exp" claim (seconds since epoch)
            val expMatch = Regex("\"exp\"\\s*:\\s*(\\d+)").find(payload)
            val expSeconds = expMatch?.groupValues?.get(1)?.toLongOrNull() ?: return true
            // Compare with current time (exp is in seconds, System.currentTimeMillis is in ms)
            System.currentTimeMillis() / 1000 > expSeconds
        } catch (e: Exception) {
            true // If we can't parse, treat as expired
        }
    }

    /**
     * Wipes all session data from both RAM cache and DataStore.
     * Called on logout to ensure no stale credentials remain.
     */
    suspend fun clearSession() {
        dataStore.edit { it.clear() }
        currentUser = null
        _userFlow.value = null
    }

    /**
     * Updates the user's currentWeight in both RAM cache and DataStore.
     * Called after successfully adding a new weight entry.
     */
    suspend fun updateCurrentWeight(weight: Double) {
        currentUser = currentUser?.copy(currentWeight = weight)
        _userFlow.value = currentUser
        dataStore.edit { it[KEY_CURRENT_WEIGHT] = weight }
    }

    // --- GOAL CELEBRATION STATE ---

    /**
     * Records that the user has been celebrated for reaching a specific goal weight.
     * Stores the goal value itself so the flag auto-resets when the goal changes.
     */
    suspend fun markGoalReached(goalWeight: Double) {
        dataStore.edit { it[KEY_GOAL_REACHED_FOR] = goalWeight.toFloat() }
    }

    /**
     * Checks if the user has already been celebrated for their current goal.
     * Returns true only if the stored value matches the current goal weight.
     */
    suspend fun isGoalAlreadyReached(currentGoalWeight: Double): Boolean {
        val prefs = dataStore.data.first()
        val stored = prefs[KEY_GOAL_REACHED_FOR] ?: return false
        return stored == currentGoalWeight.toFloat()
    }

    // --- SYNC MANAGEMENT (Offline-First Sync) ---

    /**
     * Save the timestamp of the last successful sync.
     * Used by SyncWorker to determine delta sync 'since' parameter.
     *
     * @param timestamp ISO-8601 formatted timestamp from server
     */
    suspend fun saveLastSyncTimestamp(timestamp: String) {
        dataStore.edit { it[KEY_LAST_SYNC] = timestamp }
    }

    /**
     * Get the timestamp of the last successful sync.
     * Returns null if no sync has occurred (triggers initial sync).
     */
    suspend fun getLastSyncTimestamp(): String? {
        val prefs = dataStore.data.first()
        return prefs[KEY_LAST_SYNC]
    }

    /**
     * Clear sync state (e.g., when user logs out).
     * Forces initial sync on next login.
     */
    suspend fun clearSyncState() {
        dataStore.edit { it.remove(KEY_LAST_SYNC) }
    }
}
