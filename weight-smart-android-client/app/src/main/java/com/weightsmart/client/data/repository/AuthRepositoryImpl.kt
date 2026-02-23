package com.weightsmart.client.data.repository

import com.weightsmart.client.data.SessionManager
import com.weightsmart.client.data.remote.WeightSmartApi
import com.weightsmart.client.data.remote.dto.LoginRequest
import com.weightsmart.client.data.remote.dto.RegisterRequest
import com.weightsmart.client.data.remote.dto.UpdateProfileRequest
import com.weightsmart.client.data.remote.dto.AuthResponse
import com.weightsmart.client.data.remote.dto.UserDto
import com.weightsmart.client.domain.model.User
import com.weightsmart.client.domain.repository.AuthRepository
import retrofit2.Response
import java.time.LocalDate
import javax.inject.Inject

/**
 * AuthRepositoryImpl
 * The concrete implementation of [AuthRepository] for authentication data.
 *
 * Architecture Role:
 * Implements the Repository Pattern. It mediates between the Domain layer (Business Logic)
 * and the Data Mapping layers (Network API + Local Session Cache).
 * The ViewModel depends on the [AuthRepository] interface, not this implementation,
 * enabling easy swapping for testing (e.g., MockAuthRepository).
 *
 * Key Concepts & Documentation:
 * Repository Pattern: Abstraction layer between data access and business logic.
 * <a href="https://developer.android.com/topic/architecture/data-layer">Reference: Android Data Layer</a>
 * Kotlin Result: A functional wrapper for Success/Failure states, cleaner than try/catch in UI code.
 * <a href="https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-result/">Reference: Kotlin Result</a>
 *
 * @author James Chase
 * @version 2.0 (P5: suspend SessionManager API)
 * @since 2026-01-20
 */
class AuthRepositoryImpl @Inject constructor(
    private val api: WeightSmartApi,
    private val sessionManager: SessionManager
) : AuthRepository {

    /**
     * Checks if the user is currently authenticated.
     *
     * Strategy:
     * Consults the local SessionManager for the presence of a JWT token.
     * This allows the Splash Screen to bypass the Login Screen if a valid session exists.
     *
     * @return True if a token exists in DataStore, false otherwise.
     */
    override suspend fun isLoggedIn(): Boolean {
        return sessionManager.isLoggedIn()
    }

    /**
     * Authenticates a user with the remote server.
     *
     * Workflow:
     * 1. Wraps credentials in a LoginRequest DTO.
     * 2. Executes the POST request via Retrofit.
     * 3. On Success: Caches the token and maps the response to a Domain User.
     * 4. On Failure: Returns a Result.failure with the exception.
     *
     * @param username The user's unique identifier.
     * @param password The user's password (passed as CharArray for security best practices).
     * @return Result<User> containing the User profile or an Exception.
     */
    override suspend fun login(username: String, password: CharArray): Result<User> {
        return try {
            val request = LoginRequest(username, String(password))
            password.fill('\u0000') // Zero the CharArray after String conversion
            val response = api.login(request)
            handleAuthResponse(response)
        } catch (e: Exception) {
            // Catches and wraps Network errors (no internet, DNS fail)
            Result.failure(e)
        }
    }

    /**
     * Registers a new account and immediately signs them in.
     *
     * Architecture Note:
     * This method combines creation and authentication into a single flow from the
     * client's perspective, saving the user from having to log in manually after sign-up.
     *
     * @param username Unique identifier.
     * @param email Unique contact email.
     * @param password Raw password (will be hashed by server).
     * @param age User's age (must be >13).
     * @param nickname Optional display name.
     * @param height Optional physical stat.
     * @param currentWeight Optional starting weight.
     * @param goalWeight Optional target weight.
     * @param targetDate Optional deadline for the goal.
     * @return Result<User> containing the new profile.
     */
    override suspend fun register(
        username: String,
        email: String,
        password: CharArray,
        age: Int,
        nickname: String?,
        height: Double?,
        currentWeight: Double?,
        goalWeight: Double?,
        targetDate: String?
    ): Result<User> {
        return try {
            val request = RegisterRequest(
                username = username,
                email = email,
                password = String(password),
                age = age,
                nickname = nickname,
                height = height,
                currentWeight = currentWeight,
                goalWeight = goalWeight,
                targetDate = targetDate
            )
            password.fill('\u0000') // Zero the CharArray after String conversion
            val response = api.register(request)
            handleAuthResponse(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Queries the server's Trie-based username search for registration availability.
     *
     * @param prefix The characters typed so far (e.g., "jam").
     * @return Result containing a list of matching usernames, or a failure on error.
     */
    override suspend fun searchUsernames(prefix: String): Result<List<String>> {
        return try {
            val response = api.searchUsernames(prefix)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Search failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates non-sensitive profile fields on the server.
     *
     * Workflow:
     * 1. Retrieves the JWT token from SessionManager.
     * 2. Builds an UpdateProfileRequest with the provided fields.
     * 3. Sends PUT /api/auth/update to the server.
     * 4. On success: maps the response to a domain User, caches it, returns Result.success.
     * 5. On failure: returns Result.failure with the HTTP error or exception.
     */
    override suspend fun updateProfile(
        nickname: String?,
        age: Int?,
        height: Double?,
        goalWeight: Double?,
        targetDate: String?
    ): Result<User> {
        return try {
            val token = getAuthToken()
            val request = UpdateProfileRequest(
                age = age,
                height = height,
                nickname = nickname,
                goalWeight = goalWeight,
                targetDate = targetDate
            )
            val response = api.updateProfile(token, request)
            if (response.isSuccessful && response.body() != null) {
                val user = mapUserDtoToDomain(response.body()!!)
                sessionManager.saveUser(user)
                Result.success(user)
            } else {
                Result.failure(Exception("Update failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Returns the currently stored user from the local SessionManager cache.
     * @return User if a valid session exists, null otherwise.
     */
    override suspend fun getCurrentUser(): User? {
        return sessionManager.getUser()
    }

    /**
     * Formats the stored JWT token with the "Bearer " prefix.
     * @return The Bearer-prefixed token string.
     */
    private suspend fun getAuthToken(): String {
        val token = sessionManager.getAuthToken() ?: ""
        return if (token.startsWith("Bearer ")) token else "Bearer $token"
    }

    /**
     * Maps a server [UserDto] to the domain [User] model.
     * Centralizes the DTO-to-domain mapping to avoid duplication.
     */
    private fun mapUserDtoToDomain(dto: UserDto): User {
        return User(
            id = dto.id,
            username = dto.username,
            email = dto.email,
            nickname = dto.nickname,
            age = dto.age,
            height = dto.height,
            currentWeight = dto.currentWeight,
            goalWeight = dto.goalWeight,
            targetDate = dto.targetDate?.let { LocalDate.parse(it) },
            role = dto.role
        )
    }

    /**
     * Centralized response handler for login and register endpoints.
     * On success (HTTP 200-299): caches the JWT token and user profile in SessionManager,
     * then returns a domain User wrapped in Result.success.
     * On failure: converts the HTTP error code into a Result.failure with a descriptive message.
     *
     * @param response The raw Retrofit response from the auth endpoint.
     * @return Result containing the domain User on success, or an Exception on failure.
     */
    private suspend fun handleAuthResponse(response: Response<AuthResponse>): Result<User> {
        if (response.isSuccessful && response.body() != null) {
            val body = response.body()!!
            val user = mapUserDtoToDomain(body.user)

            // Save all session data to disk (access token, refresh token, user profile)
            sessionManager.saveAuthToken(body.token)
            sessionManager.saveRefreshToken(body.refreshToken)
            sessionManager.saveUserId(body.user.id)
            sessionManager.saveUser(user)

            return Result.success(user)
        } else {
            //converts HTTP error code to exception
            return Result.failure(Exception("Error ${response.code()}: ${response.message()}"))
        }
    }
}
