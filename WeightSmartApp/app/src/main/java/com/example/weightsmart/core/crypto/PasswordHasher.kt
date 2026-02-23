package com.example.weightsmart.core.crypto

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Password hashing utilities.
 * - PBKDF2WithHmacSHA256
 * - Per-user random salt
 * - Configurable iterations
 * - Constant-time verification
 */
object PasswordHasher {
    private const val ALGO = "PBKDF2WithHmacSHA256"
    private const val SALT_BYTES = 16
    private const val KEY_LEN_BITS = 256
    private const val DEFAULT_ITER = 120_000

    private val rng = SecureRandom()

    data class HashBundle(
        val hashB64: String,
        val saltB64: String,
        val iterations: Int
    )

    /**
     * Create a new salted hash for a plaintext password (CharArray).
     * Caller may clear the CharArray after use (pw.fill('\u0000')).
     */
    fun newHash(password: CharArray, iterations: Int = DEFAULT_ITER): HashBundle {
        val salt = ByteArray(SALT_BYTES).also { rng.nextBytes(it) }
        val hash = kdf(password, salt, iterations)
        return HashBundle(
            hashB64 = Base64.encodeToString(hash, Base64.NO_WRAP),
            saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            iterations = iterations
        )
    }

    /**
     * Verify a plaintext password against a stored salt+hash.
     * Returns true iff the derived key matches (constant-time compare).
     */
    fun verify(password: CharArray, saltB64: String, hashB64: String, iterations: Int): Boolean {
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val expected = Base64.decode(hashB64, Base64.NO_WRAP)
        val actual = kdf(password, salt, iterations)
        if (actual.size != expected.size) return false
        var diff = 0
        for (i in actual.indices) diff = diff or (actual[i].toInt() xor expected[i].toInt())
        return diff == 0
    }

    private fun kdf(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_LEN_BITS)
        return SecretKeyFactory.getInstance(ALGO).generateSecret(spec).encoded
    }
}
