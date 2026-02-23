package com.weightsmart.server.controller;

import com.weightsmart.server.dto.DeltaSyncResponse;
import com.weightsmart.server.dto.GraphDataResponse;
import com.weightsmart.server.dto.WeightEntryResponse;
import com.weightsmart.server.dto.WeightLogRequest;
import com.weightsmart.server.dto.WeightStats;
import com.weightsmart.server.model.WeightEntry;
import com.weightsmart.server.service.WeightService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/*
 * WeightController
 * The public REST API entry point for all weight tracking operations under /api/weights/** :
 * 1) POST   /api/weights/{userId}       -> Log a new weight entry.
 * 2) GET    /api/weights/{userId}       -> Retrieve paginated history.
 * 3) GET    /api/weights/{userId}/stats -> Calculate stats (Total Loss, Rate) for a date range.
 * 4) GET    /api/weights/{userId}/export-> Download history as a CSV file.
 * 5) DELETE /api/weights/{weightId}     -> Soft delete a specific entry.
 *
 * REQUEST LIFECYCLE QUICK MAP
 * 1) Add Weight:
 * Controller (Validate DTO) -> Service (Business Logic) -> Repository (Save) -> Controller (Map to Safe DTO)
 * 2) Get History:
 * Controller (Parse Pageable) -> Service (FindAll Page) -> Controller (Map Page<Entity> to Page<DTO>)
 * 3) Stats:
 * Controller (Parse Dates) -> Service (Aggregate Queries) -> Return WeightStats DTO
 *
 * Architecture Role:
 * This controller acts as the data interface between the Android Client's "Home" & "Table" screens
 * and the backend database. It strictly enforces data safety by stripping sensitive User info
 * from weight records before sending them to the client.
 *
 * Key Concepts & Documentation:
 * RequestMapping: Defines the base URL for all endpoints in this class.
 * <a href="https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html">Reference: @RequestMapping</a>
 * Pageable: Automatically parses URL params (?page=0&size=10&sort=date) into a pagination object.
 * <a href="https://docs.spring.io/spring-data/commons/docs/current/api/org/springframework/data/domain/Pageable.html">Reference: Spring Data Pageable</a>
 * DateTimeFormat: Parses ISO-8601 string dates (2026-01-20T10:00:00) into Java LocalDateTime objects.
 * <a href="https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/format/annotation/DateTimeFormat.html">Reference: @DateTimeFormat</a>
 * Content-Disposition: HTTP Header used to trigger file downloads (CSV Export).
 * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Disposition">Reference: MDN Headers</a>
 *
 * @author James Chase
 * @version 1.1
 * @since 2026-01-17
 */

@RestController
@RequestMapping("/api/weights")
public class WeightController {

    private final WeightService weightService;

    /**
     * Constructor Injection.
     * Spring IoC container automatically provides the WeightService instance.
     *
     * @param weightService The business logic layer for weight operations.
     */
    @Autowired
    public WeightController(WeightService weightService) {
        this.weightService = weightService;
    }

    /**
     * Endpoint: ADD WEIGHT entry.
     * URL: POST /api/weights/{userId}
     *
     * Role:
     * Accepts a raw request, validates it, and saves it.
     * We accept {userId} in the path to support potential "Admin logging for user" features later,
     * though currently, the SecurityConfig ensures users can only log for themselves.
     *
     * @param userId  The ID of the user owning this record.
     * @param request The JSON body containing weight value and date. Validated by @Valid against DTO constraints.
     * @return WeightEntryResponse (Safe DTO) with the generated UUID.
     */
    @PostMapping("/{userId}")
    public ResponseEntity<WeightEntryResponse> addWeight(
            @PathVariable Long userId,
            @Valid @RequestBody WeightLogRequest request) {

        // Use client's UUID if provided (idempotent push), otherwise generate a new one
        UUID entryId = (request.getId() != null) ? request.getId() : UUID.randomUUID();

        // Idempotency check: if this UUID already exists, return the existing entry
        // This prevents duplicates when the client retries a push after a network timeout
        Optional<WeightEntry> existing = weightService.findById(entryId);
        if (existing.isPresent()) {
            return ResponseEntity.ok(mapToResponse(existing.get()));
        }

        // Map DTO to Entity
        WeightEntry entry = new WeightEntry();
        entry.setId(entryId);
        entry.setWeight(request.getWeight());
        entry.setDate(request.getDate());

        // Call Service: Assigns user relationship and saves to DB
        WeightEntry saved = weightService.addWeightEntry(userId, entry);

        // Convert Entity back to Safe DTO (strips User object)
        return ResponseEntity.ok(mapToResponse(saved));
    }

    /**
     * Endpoint: GET HISTORY (Paginated).
     * URL: GET /api/weights/{userId}?page=0&size=10&sort=date,desc
     *
     * Role:
     * Fetches a page of the user's history for the Table View.
     * Uses Spring Data's `Pageable` interface
     *
     * @param userId   The owner of the records.
     * @param pageable Automatically constructed from URL query params (page, size, sort).
     * @return A Page of WeightEntryResponse objects, including metadata (totalPages, totalElements).
     */
    @GetMapping("/{userId}")
    public ResponseEntity<Page<WeightEntryResponse>> getUserHistory(
            @PathVariable Long userId,
            Pageable pageable) {

        // Get the Raw Page of Entities from service
        Page<WeightEntry> rawPage = weightService.getUserHistory(userId, pageable);

        //  Convert Page<Entity> -> Page<DTO>
        // Iterates mapping function over records in page to convert to safe DTO
        Page<WeightEntryResponse> safePage = rawPage.map(this::mapToResponse);

        return ResponseEntity.ok(safePage);
    }

    /**
     * Endpoint: GET STATISTICS (Graph Data).
     * URL: GET /api/weights/{userId}/stats?start=...&end=...
     *
     * Role:
     * Calculates aggregate metrics (Total Change, Weekly Rate) for the Home Screen graph.
     *
     * @param userId The user to analyze.
     * @param start  Start of the range (ISO-8601 Format).
     * @param end    End of the range (ISO-8601 Format).
     * @return WeightStats DTO containing pre-calculated metrics.
     */
    @GetMapping("/{userId}/stats")
    public ResponseEntity<WeightStats> getStats(
            @PathVariable Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        WeightStats stats = weightService.calculateStatistics(userId, start, end);
        return ResponseEntity.ok(stats);
    }

    /**
     * Endpoint: EXPORT CSV.
     * URL: GET /api/weights/{userId}/export
     *
     * Role:
     * Generates a downloadable CSV file of the user's entire history.
     * Sets specific HTTP Headers to tell the Android Browser/OS to treat this response as a file download.
     *
     * @param userId The user requesting the export.
     * @return Byte Array of the CSV file.
     */
    @GetMapping("/{userId}/export")
    public ResponseEntity<byte[]> exportCsv(@PathVariable Long userId) {
        byte[] csvData = weightService.generateWeightHistoryCsv(userId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=weights.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvData);
    }

    /**
     * Endpoint: DELETE WEIGHT entry.
     * URL: DELETE /api/weights/{weightId}
     *
     * Role:
     * Performs a soft-delete on a specific weight entry.
     *
     * @param weightId The ID of the weight entry to remove.
     * @return 204 No Content (Standard REST success response for deletes).
     */
    @DeleteMapping("/{weightId}")
    public ResponseEntity<Void> deleteWeight(@PathVariable UUID weightId) {
        weightService.deleteWeightEntry(weightId);
        return ResponseEntity.noContent().build();
    }

    // --- GRAPH DATA (P8: Analytics Screen) ---

    /**
     * Endpoint: GET GRAPH DATA.
     * URL: GET /api/weights/{userId}/graph?period=MONTH
     *
     * Role:
     * Returns plot-ready data points for the Analytics line chart.
     * Applies adaptive downsampling for large datasets:
     * - RAW for ≤500 entries or short periods (WEEK, MONTH)
     * - WEEKLY averages for >500 entries
     * - MONTHLY averages if weekly still exceeds 500
     *
     * @param userId The user to graph.
     * @param period Time period: WEEK, MONTH, YEAR, FIVE_YEAR, or LIFETIME.
     * @return GraphDataResponse with data points, resolution, and summary stats.
     */
    @GetMapping("/{userId}/graph")
    public ResponseEntity<GraphDataResponse> getGraphData(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "MONTH") String period) {

        GraphDataResponse response = weightService.getGraphData(userId, period);
        return ResponseEntity.ok(response);
    }

    // --- DELTA SYNC (P0: Offline-First Synchronization) ---

    /**
     * Endpoint: DELTA SYNC (Pull Changes).
     * URL: GET /api/weights/{userId}/sync?since={timestamp}
     *
     * Role:
     * Returns all weight entries modified after the given timestamp.
     * Includes tombstones (soft-deleted records) so client can remove them locally.
     * Used by Android SyncWorker for ongoing delta synchronization.
     *
     * Note: Initial sync (100 records on login) uses the existing
     * getUserHistory endpoint with pagination, not this endpoint.
     *
     * @param userId   The user ID.
     * @param since    ISO-8601 timestamp of last successful sync.
     * @param pageable Pagination params (page, size). Default size=50.
     * @return DeltaSyncResponse containing entries and sync metadata.
     */
    @GetMapping("/{userId}/sync")
    public ResponseEntity<DeltaSyncResponse> syncWeights(
            @PathVariable Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            Pageable pageable) {

        DeltaSyncResponse response = weightService.getDeltaSync(userId, since, pageable);
        return ResponseEntity.ok(response);
    }

    // --- HELPER: MAPPER ---

    /**
     * Converts a raw Database Entity into a clean JSON Response.
     * Prevents infinite recursion (User -> Weight -> User) and hides internal fields.
     * Includes sync fields (createdAt, updatedAt) for offline-first synchronization.
     */
    private WeightEntryResponse mapToResponse(WeightEntry entry) {
        return WeightEntryResponse.builder()
                .id(entry.getId())
                .weight(entry.getWeight())
                .date(entry.getDate())
                .isDeleted(entry.getIsDeleted())
                .createdAt(entry.getCreatedAt())
                .updatedAt(entry.getUpdatedAt())
                .build();
    }
}