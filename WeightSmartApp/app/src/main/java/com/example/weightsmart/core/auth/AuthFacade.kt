package com.example.weightsmart.core.auth

import com.example.weightsmart.core.session.Session
import com.example.weightsmart.core.session.SessionManager
import com.example.weightsmart.data.repository.UserRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin façade for auth flows:
 * - register (hashing done in UserRepository)
 * - login (sets encrypted session)
 * - logout
 * - permission checks (RBAC)
 */
@Singleton
class AuthFacade @Inject constructor(
    private val repo: UserRepository,
    private val session: SessionManager
) {
    /** Create a user. Password is hashed inside UserRepository. */
    suspend fun register(
        username: String,
        email: String,
        role: Role,
        password: CharArray,
        nickname: String? = null,
        avatar: String = "avatar_default"
    ): Result<Long> = runCatching {
        require(username.isNotBlank()) { "Username required" }
        require(email.isNotBlank()) { "Email required" }
        repo.createUser(
            username = username,
            email = email,
            role = role,
            password = password,
            nickname = nickname,
            avatar = avatar
        )
    }

    /** Verify credentials, start a session on success. */
    suspend fun login(username: String, password: CharArray): Result<Session> = runCatching {
        val user = repo.authenticate(username, password) ?: error("Invalid credentials")
        val sess = Session(userId = user.id, username = user.username, role = Role.valueOf(user.role))
        session.setSession(sess)
        sess
    }

    /** Clear the session. */
    suspend fun logout() {
        session.setSession(null)
    }

    /** RBAC guard: check current session's role for a permission. */
    suspend fun requirePermission(perm: Permission): Boolean {
        val sess = session.getSession() ?: return false
        return Rbac.grants(sess.role, perm)
    }

    /** Convenience: read current session (or null). */
    suspend fun currentSession(): Session? = session.getSession()
}
