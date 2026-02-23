package com.weightsmart.client.data.remote.dto

/**
 * RestPage
 * A generic wrapper that deserializes Spring Boot's Page JSON structure.
 *
 * Architecture Role:
 * Spring Boot's PageImpl serializes to a JSON object with fields like "content",
 * "totalPages", etc. This class mirrors that shape so GSON can deserialize it
 * without needing a custom TypeAdapter. Used as the response type for any
 * paginated endpoint (e.g., GET /api/weights/{userId}).
 *
 * @param T The type of items contained in the page (e.g., WeightEntryResponse).
 * @property content The list of items for this page.
 * @property totalPages Total number of pages available on the server.
 * @property totalElements Total number of items across all pages.
 * @property last True if this is the final page (no more data to fetch).
 * @property size Number of items per page (as requested).
 * @property number Current page number (0-indexed).
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-01-20
 */
data class RestPage<T>(
    val content: List<T>,
    val totalPages: Int,
    val totalElements: Long,
    val last: Boolean,
    val size: Int,
    val number: Int
)