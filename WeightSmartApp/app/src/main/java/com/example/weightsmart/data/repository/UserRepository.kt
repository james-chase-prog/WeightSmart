package com.example.weightsmart.data.repository

import com.example.weightsmart.core.auth.Role
import com.example.weightsmart.core.crypto.PasswordHasher
import com.example.weightsmart.data.dao.UserDao
import com.example.weightsmart.data.entity.UserEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao
) {
    /** Create a user; hashes the password and stores KDF values. */
    suspend fun createUser(
        username: String,
        email: String,
        role: Role = Role.USER,
        password: CharArray,
        nickname: String? = null,
        avatar: String = "avatar_default"
    ): Long {
        val hb = PasswordHasher.newHash(password)
        password.fill('\u0000')

        val entity = UserEntity(
            username = username.trim(),
            email = email.trim(),
            passwordHashB64 = hb.hashB64,
            saltB64 = hb.saltB64,
            iterations = hb.iterations,
            role = role.name,
            nickname = nickname?.trim()?.ifBlank { null },
            currentWeight = null,
            currentWeightEpochSec = null,
            phoneNumber = null,
            smsConsent = null,
            avatar = avatar
        )
        return userDao.insert(entity)
    }

    /** Authenticate: verify password against stored PBKDF2 hash. */
    suspend fun authenticate(username: String, password: CharArray): UserEntity? {
        val found = userDao.findByUsername(username.trim()) ?: return null
        val ok = PasswordHasher.verify(password, found.saltB64, found.passwordHashB64, found.iterations)
        password.fill('\u0000')
        return if (ok) found else null
    }

    /** Change password for a given username. */
    suspend fun changePassword(username: String, newPassword: CharArray) {
        val user = userDao.findByUsername(username.trim()) ?: return
        val hb = PasswordHasher.newHash(newPassword)
        newPassword.fill('\u0000')
        userDao.update(
            user.copy(
                passwordHashB64 = hb.hashB64,
                saltB64 = hb.saltB64,
                iterations = hb.iterations
            )
        )
    }

    /** Load a user by id (for profile screens). */
    suspend fun getById(userId: Long): UserEntity? = userDao.findById(userId)

    /** Update lightweight profile fields. */
    suspend fun updateProfile(
        userId: Long,
        nickname: String?,
        phoneNumber: String?,
        smsConsent: Boolean?,
        avatar: String?
    ) {
        val user = userDao.findById(userId) ?: return
        userDao.update(
            user.copy(
                nickname = nickname?.trim()?.ifBlank { null },
                phoneNumber = phoneNumber?.trim()?.ifBlank { null },
                smsConsent = smsConsent,
                avatar = avatar ?: user.avatar
            )
        )
    }

    /** Targeted SMS settings update (no other fields touched). */
    suspend fun updateSmsSettings(
        userId: Long,
        phoneNumber: String?,
        smsConsent: Boolean
    ) {
        userDao.updateSmsSettings(
            userId = userId,
            phoneNumber = phoneNumber?.trim()?.ifBlank { null },
            smsConsent = smsConsent
        )
    }
}

