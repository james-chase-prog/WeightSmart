package com.weightsmart.client.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.google.crypto.tink.Aead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * AppModule
 * Provides system-level dependencies that don't fit into Network or Database modules.
 *
 * Architecture Role:
 * Exposes Android System Services to the Hilt dependency graph.
 * Provides Jetpack DataStore + Google Tink (Aead) for secure session storage.
 *
 * Key Concepts & Documentation:
 * DataStore: Modern replacement for SharedPreferences with coroutine-native async I/O.
 * <a href="https://developer.android.com/topic/libraries/architecture/datastore">Reference: Jetpack DataStore</a>
 * Tink: Google's multi-language, cross-platform cryptographic library.
 * <a href="https://developers.google.com/tink">Reference: Google Tink</a>
 * Aead (Authenticated Encryption with Associated Data): AES-256-GCM encryption primitive.
 * <a href="https://developers.google.com/tink/aead">Reference: Tink AEAD</a>
 *
 * @author James Chase
 * @version 2.1 (P5: Removed legacy EncryptedSharedPreferences — not deployed)
 * @since 2026-01-20
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Provides Jetpack DataStore for session persistence.
     * Replaces EncryptedSharedPreferences as the primary storage mechanism.
     * Coroutine-native: avoids ANR risk from synchronous disk reads.
     */
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("weight_smart_session") }
        )
    }

    /**
     * Provides Google Tink Aead for encrypting sensitive values before storing in DataStore.
     * Uses AES-256-GCM with Android Keystore-backed master key (hardware-backed on supported devices).
     * Sensitive values (auth token, email) are encrypted; non-sensitive values stored as plaintext.
     */
    @Provides
    @Singleton
    fun provideAead(@ApplicationContext context: Context): Aead {
        AeadConfig.register()
        val keysetHandle = AndroidKeysetManager.Builder()
            .withSharedPref(context, "tink_keyset", "tink_prefs")
            .withKeyTemplate(AeadKeyTemplates.AES256_GCM)
            .withMasterKeyUri("android-keystore://weightsmart_master_key")
            .build()
            .keysetHandle
        return keysetHandle.getPrimitive(Aead::class.java)
    }
}
