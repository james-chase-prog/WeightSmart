package com.weightsmart.client.data.remote.dto

/**
 * WeightsModel.kt
 * Data Transfer Objects (DTOs) for weight entry, statistics, and pagination endpoints.
 *
 * Architecture Role:
 * These classes mirror the Spring Boot server DTOs (com.weightsmart.server.dto.*)
 * and the WeightController.java response shapes. GSON handles serialization and
 * deserialization automatically via Retrofit's GsonConverterFactory. Weight DTOs are
 * mapped to Room entities via extension functions in WeightMappers.kt.
 *
 * Key Concepts & Documentation:
 * ISO-8601: International standard for date/time string representation (e.g., "2026-01-21T10:00:00").
 * <a href="https://en.wikipedia.org/wiki/ISO_8601">Reference: ISO 8601</a>
 *
 * @author James Chase
 * @version 1.1
 * @since 2026-01-20
 */

/**
 * WeightLogRequest
 * Sent to POST /api/weights/{userId} to record a new weight entry.
 * Matches server: WeightLogRequest.java
 *
 * @property weight The recorded weight value.
 * @property date ISO-8601 datetime string (e.g., "2026-01-21T10:00:00").
 */
data class WeightLogRequest(
    val id: String? = null,
    val weight: Double,
    val date: String
)

/**
 * WeightEntryResponse
 * Returned by the server for individual weight entries.
 * Matches server: WeightEntryResponse.java
 *
 * @property id Server-assigned UUID for the weight entry.
 * @property weight The recorded weight value.
 * @property date ISO-8601 datetime string for when the weight was recorded.
 * @property isDeleted Soft-delete flag; true means this entry is a tombstone for sync cleanup.
 * @property createdAt ISO-8601 timestamp of when the entry was first created on the server.
 * @property updatedAt ISO-8601 timestamp of the last modification; used as the cursor for delta sync.
 */
data class WeightEntryResponse(
    val id: String,
    val weight: Double,
    val date: String,
    // --- Sync Fields ---
    val isDeleted: Boolean? = false,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

/**
 * WeightStats
 * Deserialized response from GET /api/weights/{userId}/stats.
 * Contains computed analytics for graphing and dashboard display.
 *
 * @property rangeStartDate Start of the analysis period (ISO-8601 string).
 * @property rangeEndDate End of the analysis period (ISO-8601 string).
 * @property startingWeight First weight value in the range.
 * @property endingWeight Last weight value in the range.
 * @property minWeight Lowest weight recorded in the range.
 * @property maxWeight Highest weight recorded in the range.
 * @property totalLogs Number of weight entries in the range.
 * @property totalChange Net change (endingWeight - startingWeight).
 * @property weeklyChangeRate Average weekly rate of change (slope).
 */
data class WeightStats(
    // --- Range Definition ---
    val rangeStartDate: String?,
    val rangeEndDate: String?,

    // --- Descriptive Stats ---
    val startingWeight: Double?,
    val endingWeight: Double?,
    val minWeight: Double?,
    val maxWeight: Double?,

    val totalLogs: Int,

    // --- Analysis ---
    val totalChange: Double?,
    val weeklyChangeRate: Double?
)

/**
 * GraphDataPoint
 * A single data point for the weight trend chart.
 * Matches server: GraphDataPoint.java
 *
 * @property date ISO-8601 datetime string (raw entry date or downsampled group midpoint).
 * @property weight Weight value in lbs (raw entry weight or group average).
 */
data class GraphDataPoint(
    val date: String,
    val weight: Double
)

/**
 * GraphDataResponse
 * Response from GET /api/weights/{userId}/graph.
 * Matches server: GraphDataResponse.java
 *
 * @property dataPoints Plot data (raw or downsampled depending on dataset size).
 * @property resolution Resolution indicator: "RAW", "WEEKLY", or "MONTHLY".
 * @property totalRecords Original record count before downsampling.
 * @property totalChange Net weight change (last - first). Null if < 2 records.
 * @property weeklyChangeRate Average change per week. Null if < 2 records.
 */
data class GraphDataResponse(
    val dataPoints: List<GraphDataPoint>,
    val resolution: String,
    val totalRecords: Int,
    val totalChange: Double?,
    val weeklyChangeRate: Double?
)