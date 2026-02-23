package com.weightsmart.client.di

import com.weightsmart.client.data.repository.AuthRepositoryImpl
import com.weightsmart.client.domain.repository.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * RepositoryModule
 * Binds abstract repository interfaces to their concrete implementations.
 *
 * Architecture Role:
 * Implements the Dependency Inversion Principle: ViewModels and Use Cases
 * depend on the domain-layer interface (AuthRepository), while this module
 * tells Hilt to provide the data-layer implementation (AuthRepositoryImpl).
 * This keeps the domain layer free of framework dependencies.
 *
 * Key Concepts & Documentation:
 * @Binds: Lightweight alternative to @Provides -- generates less bytecode because
 *   it only needs to map an interface to an existing implementation (no factory method body).
 * <a href="https://dagger.dev/dev-guide/">Reference: Dagger Developer Guide</a>
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-01-20
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    /**
     * Binds AuthRepository interface to AuthRepositoryImpl.
     * Satisfies: AuthViewModel, any use case that needs login/register/logout operations.
     * AuthRepositoryImpl handles Retrofit API calls and SessionManager token storage.
     */
    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository
}