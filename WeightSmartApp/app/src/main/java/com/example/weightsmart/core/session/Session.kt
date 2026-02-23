package com.example.weightsmart.core.session

import com.example.weightsmart.core.auth.Role

/**
 * In-memory representation of a logged-in session.
 */
data class Session(
    val userId: Long,
    val username: String,
    val role: Role
)

/**
 * Contract for storing/retrieving the current session.
 * Implementations may use encrypted SharedPreferences, DataStore, etc.
 */
interface SessionManager {
    /** Set/replace the current session; pass null to clear. */
    suspend fun setSession(session: Session?)

    /** Get the current session, or null if not logged in. */
    suspend fun getSession(): Session?

    /** Convenience helper. */
    suspend fun isLoggedIn(): Boolean = getSession() != null
}
