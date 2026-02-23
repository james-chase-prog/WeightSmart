// core/session/EncryptedPrefsSessionManager.kt
package com.example.weightsmart.core.session

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.weightsmart.core.auth.Role
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SessionManager backed by AndroidX Security Crypto's EncryptedSharedPreferences.
 * Values are encrypted at rest with a MasterKey stored in Android Keystore.
 */
@Singleton
class EncryptedPrefsSessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) : SessionManager {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override suspend fun setSession(session: Session?) = withContext(Dispatchers.IO) {
        prefs.edit().apply {
            if (session == null) {
                remove(KEY_ID)
                remove(KEY_USERNAME)
                remove(KEY_ROLE)
            } else {
                putLong(KEY_ID, session.userId)
                putString(KEY_USERNAME, session.username)
                putString(KEY_ROLE, session.role.name)
            }
        }.apply()
    }

    override suspend fun getSession(): Session? = withContext(Dispatchers.IO) {
        val id = prefs.getLong(KEY_ID, -1L)
        val username = prefs.getString(KEY_USERNAME, null)
        val roleName = prefs.getString(KEY_ROLE, null)

        if (id <= 0 || username.isNullOrBlank() || roleName.isNullOrBlank()) return@withContext null
        val role = try { Role.valueOf(roleName) } catch (_: IllegalArgumentException) { null } ?: return@withContext null
        Session(userId = id, username = username, role = role)
    }

    companion object {
        private const val PREFS_NAME = "ws_session"
        private const val KEY_ID = "userId"
        private const val KEY_USERNAME = "username"
        private const val KEY_ROLE = "role"
    }
}
