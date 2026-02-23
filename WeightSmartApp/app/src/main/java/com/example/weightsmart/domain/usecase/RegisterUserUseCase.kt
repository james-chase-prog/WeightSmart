package com.example.weightsmart.domain.usecase

import com.example.weightsmart.core.auth.AuthFacade
import com.example.weightsmart.core.auth.Role
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Register a new user. Returns Result<userId>.
 * Clears the provided CharArray after use.
 */
@Singleton
class RegisterUserUseCase @Inject constructor(
    private val auth: AuthFacade
) {
    suspend operator fun invoke(
        username: String,
        email: String,
        password: CharArray,
        nickname: String? = null
    ): Result<Long> {
        val res = auth.register(
            username = username,
            email = email,
            role = Role.USER,
            password = password,
            nickname = nickname
        )
        password.fill('\u0000')
        return res
    }
}
