package com.example.weightsmart.di

import com.example.weightsmart.core.auth.AuthFacade
import com.example.weightsmart.core.session.EncryptedPrefsSessionManager
import com.example.weightsmart.core.session.SessionManager
import com.example.weightsmart.data.repository.UserRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    /** Bind the interface to the encrypted implementation. */
    @Provides
    @Singleton
    fun provideSessionManager(impl: EncryptedPrefsSessionManager): SessionManager = impl

    /** Provide AuthFacade (depends on UserRepository + SessionManager). */
    @Provides
    @Singleton
    fun provideAuthFacade(
        repo: UserRepository,
        session: SessionManager
    ): AuthFacade = AuthFacade(repo, session)
}
