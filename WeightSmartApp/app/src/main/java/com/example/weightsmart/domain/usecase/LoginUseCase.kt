package com.example.weightsmart.domain.usecase

import com.example.weightsmart.core.session.Session
import com.example.weightsmart.core.auth.AuthFacade
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Log in with username/password. Returns Result<Session>.
 * Clears the provided CharArray after use.
 */
@Singleton
class LoginUseCase @Inject constructor(
    private val auth: AuthFacade
) {
    suspend operator fun invoke(username: String, password: CharArray): Result<Session> {
        val res = auth.login(username, password)
        // clear in all cases
        password.fill('\u0000')
        return res
    }
}
