package com.weightsmart.client.data.remote

import com.weightsmart.client.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

/**
 * WeightSmartApi
 * The declarative HTTP client interface for the WeightSmart Android App.
 *
 * API ENDPOINT INDEX:
 * --- Auth Operations ---
 * 1) POST   /api/auth/register    -> Register new user & issue JWT.
 * 2) GET    /api/auth/search-usernames -> Returns response for username availability
 * 3) POST   /api/auth/login       -> Authenticate credentials & issue JWT.
 * 4) PUT    /api/auth/update      -> Update non-sensitive profile fields.
 *
 * --- Weight Operations ---
 * 5) POST   /api/weights/{userId}       -> Log a new weight entry.
 * 6) GET    /api/weights/{userId}       -> Retrieve paginated history.
 * 7) GET    /api/weights/{userId}/stats -> Fetch graph data (Total Change, Rate).
 * 8) DELETE /api/weights/{weightId}    -> Soft delete an entry.
 *
 * DATA FLOW QUICK MAP
 * 1) Request:
 * ViewModel (State) -> Repository (Domain Logic) -> API Interface (Params) -> Retrofit (Serialize JSON) -> HTTP
 * 2) Response:
 * HTTP (Raw Bytes) -> OkHttp (Interceptor Logging) -> Retrofit (Deserialize DTO) -> Repository (Result<T>)
 *
 * Architecture Role:
 * This interface defines the contract between the Android Client and the Spring Boot Backend.
 * It is the "Edge" of the Data Layer. It uses Kotlin Coroutines ('suspend') to ensure
 * all network operations are performed off the Main Thread, preventing UI freezes (ANRs).
 *
 * Key Concepts & Documentation:
 * Retrofit: A type-safe HTTP client that turns this interface into a runnable Java class.
 * <a href="https://square.github.io/retrofit/">Reference: Retrofit Documentation</a>
 * Suspend Functions: Kotlin's native support for asynchronous, non-blocking code.
 * <a href="https://kotlinlang.org/docs/coroutines-overview.html">Reference: Kotlin Coroutines</a>
 * HTTP Annotations: Decorators (@GET, @POST) that map functions to REST verbs.
 * <a href="https://square.github.io/retrofit/2.x/retrofit/retrofit2/http/package-summary.html">Reference: Retrofit Annotations</a>
 *
 * @author James Chase
 * @version 1.2
 * @since 2026-01-21
 */
interface WeightSmartApi {

    /**
     * URL: POST /api/auth/register
     * Input: RegisterRequest (username, email, password, profile stats)
     * Output: AuthResponse (JWT Token + User Profile)
     */
    @POST("/api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    /**
     * URL: GET /api/auth/search-usernames
     * Input: prefix String (username field of registration)
     * Output: Response of of prefix trie query as List
     */
    @GET("/api/auth/search-usernames")
    suspend fun searchUsernames(@Query("prefix") prefix: String): Response<List<String>>

    /**
     * URL: POST /api/auth/login
     * Input: LoginRequest (username, password)
     * Output: AuthResponse (JWT Token + User Profile)
     */
    @POST("/api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    /**
     * URL: PUT /api/auth/update
     * Input: UpdateProfileRequest (nickname, age, height, goalWeight, targetDate)
     * Output: UserResponse (The updated profile)
     * Note: Requires JWT Token in Header
     */
    @PUT("/api/auth/update")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body request: UpdateProfileRequest
    ): Response<UserDto>

    /**
     * URL: POST /api/weights/{userId}
     * Input: WeightLogRequest (weight, date)
     * Output: WeightEntryResponse (Safe DTO)
     * Note: Server explicitly requires userId in the path.
     */
    @POST("/api/weights/{userId}")
    suspend fun addWeight(
        @Path("userId") userId: Long,
        @Header("Authorization") token: String,
        @Body request: WeightLogRequest // Matches server DTO name
    ): Response<WeightEntryResponse>

    /**
     * URL: GET /api/weights/{userId}
     * Input: Pageable params (page, size, sort)
     * Output: RestPage<WeightEntryResponse> (Wrapper for Spring's Page JSON)
     */
    @GET("/api/weights/{userId}")
    suspend fun getUserHistory(
        @Path("userId") userId: Long,
        @Header("Authorization") token: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 10,
        @Query("sort") sort: String? = "date,desc"
    ): Response<RestPage<WeightEntryResponse>>

    /**
     * URL: GET /api/weights/{userId}/stats
     * Input: Date range (ISO 8601 Strings, e.g., "2026-01-23T10:00:00")
     * Output: WeightStats (Graph data)
     */
    @GET("/api/weights/{userId}/stats")
    suspend fun getStats(
        @Path("userId") userId: Long,
        @Header("Authorization") token: String,
        @Query("start") startIso: String,
        @Query("end") endIso: String
    ): Response<WeightStats>

    /**
     * URL:  SOFT DELETE /api/weights/{weightId}
     * Output: 204 No Content
     */
    @DELETE("/api/weights/{weightId}")
    suspend fun deleteWeight(
        @Path("weightId") weightId: String,
        @Header("Authorization") token: String
    ): Response<Void>

    // --- GRAPH DATA (P8: Analytics Screen) ---

    /**
     * URL: GET /api/weights/{userId}/graph?period=MONTH
     * Input: Time period (WEEK, MONTH, YEAR, FIVE_YEAR, LIFETIME)
     * Output: GraphDataResponse (plot data + resolution + stats)
     *
     * Returns adaptively downsampled graph data:
     * - RAW for ≤500 entries or short periods
     * - WEEKLY/MONTHLY averages for large datasets
     */
    @GET("/api/weights/{userId}/graph")
    suspend fun getGraphData(
        @Path("userId") userId: Long,
        @Header("Authorization") token: String,
        @Query("period") period: String = "MONTH"
    ): Response<GraphDataResponse>

    // --- DELTA SYNC (Offline-First Synchronization) ---

    /**
     * URL: GET /api/weights/{userId}/sync?since={timestamp}
     * Input: 'since' timestamp (ISO-8601) from last successful sync
     * Output: DeltaSyncResponse containing changed entries and metadata
     *
     * Use Cases:
     * - Called by SyncWorker after initial sync is complete
     * - Returns only records modified after the given timestamp
     * - Includes tombstones (isDeleted=true) for local cleanup
     *
     * Note: Initial sync uses getUserHistory() with pagination, not this endpoint.
     */
    @GET("/api/weights/{userId}/sync")
    suspend fun deltaSync(
        @Path("userId") userId: Long,
        @Header("Authorization") token: String,
        @Query("since") since: String,
        @Query("size") size: Int = 50
    ): Response<DeltaSyncResponse>
}


