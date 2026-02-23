package com.example.weightsmart.domain.usecase

import com.example.weightsmart.core.auth.AuthFacade
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogoutUseCase @Inject constructor(
    private val auth: AuthFacade
) {
    suspend operator fun invoke() = auth.logout()
}
