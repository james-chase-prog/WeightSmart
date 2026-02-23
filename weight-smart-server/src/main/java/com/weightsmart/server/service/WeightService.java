package com.weightsmart.server.service;

import com.weightsmart.server.dto.DeltaSyncResponse;
import com.weightsmart.server.dto.GraphDataPoint;
import com.weightsmart.server.dto.GraphDataResponse;
import com.weightsmart.server.dto.WeightEntryResponse;
import com.weightsmart.server.dto.WeightStats;
import com.weightsmart.server.model.User;
import com.weightsmart.server.model.WeightEntry;
import com.weightsmart.server.repository.UserRepository;
import com.weightsmart.server.repository.WeightRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.stream.Collectors;

/*
 * WeightService
 * Handles the business logic for recording, retrieving, and deleting weights.
 *
 * Architecture Role:
 * 1. Data Integrity: Ensures every weight is linked to a valid User.
 * 2. Caching: Updates the User's "currentWeight" field automatically upon insertion.
 * 3. Sync Safety: Implements "Soft Deletes" (Tombstones) instead of physical removal.
 * 4. Analytics: Performs server-side math for rates of change and exports.
 *
 * Key Concepts and Documentation
 * @Transactional: <a href="https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html">@Transactional</a>:
 * Ensures atomicity. If saving the weight fails, the User profile update is also rolled back.
 * Slice: <a href="https://docs.spring.io/spring-data/commons/docs/current/api/org/springframework/data/domain/Page.html">Page Interface</a>:
 * The return type used for pagination. Unlike Slice, Page provides total record counts, enabling "Page 1 of 5" footers for the Web Client.
 *
 * @author James Chase
 * @version 1.1
 * @since 2026-01-16
 */

@Service
public class WeightService {

    private final WeightRepository weightRepository;
    private final UserRepository userRepository;

    @Autowired
    public WeightService(WeightRepository weightRepository, UserRepository userRepository) {
        this.weightRepository = weightRepository;
        this.userRepository = userRepository;
    }

    // --- LOOKUP ---

    /**
     * Finds a weight entry by its UUID.
     * Used by the controller for idempotent push checks — if a client-provided
     * UUID already exists, the existing entry is returned instead of creating a duplicate.
     *
     * @param id The UUID of the weight entry.
     * @return Optional containing the entry if found, empty otherwise.
     */
    @Transactional(readOnly = true)
    public Optional<WeightEntry> findById(UUID id) {
        return weightRepository.findById(id);
    }

    // --- ADDING DATA ---

    /**
     * Records a new weight entry and updates the User's summary.
     *
     * @param userId The ID of the user.
     * @param entry The weight data (weight, date).
     * @return The saved entry.
     */
    @Transactional
    public WeightEntry addWeightEntry(Long userId, WeightEntry entry) {
        // Validate User exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Link Entry to User
        entry.setUser(user);

        // Save the Weight Entry
        WeightEntry savedEntry = weightRepository.save(entry);

        // Update the User's Cache (The "Current Weight")
        // Query for the actual most recent active entry by date — handles back-dated entries
        // where the new entry's date is older than existing entries.
        updateCurrentWeightCache(user);

        return savedEntry;
    }

    // --- RETRIEVING DATA (PAGINATED) ---

    /**
     * Retrieves scrolling history for a specific user.
     * Returns a PAGE (includes total count) to support Web Tables and Android Prev/Next buttons.
     *
     * @param userId The User ID.
     * @param pageable Pagination info (page number, size).
     * @return A Page of entries. Includes metadata like "isFirst", "isLast", and "totalElements".
     */
    public Page<WeightEntry> getUserHistory(Long userId, Pageable pageable) {
        // Efficiency: getReferenceById creates a proxy without hitting the DB.
        User user = userRepository.getReferenceById(userId);
        return weightRepository.findByUserOrderByDateDesc(user, pageable);
    }

    // --- RETRIEVING DATA (GRAPHS & EXPORTS) ---

    /**
     * Retrieves raw data points for graphing within a specific date range.
     * Used internally by getGraphData() and calculateStatistics().
     *
     * @param userId The User ID.
     * @param start The start date (inclusive).
     * @param end The end date (inclusive).
     * @return List of WeightEntry objects (Ordered chronologically).
     */
    private List<WeightEntry> getWeightsForGraph(Long userId, LocalDateTime start, LocalDateTime end) {
        User user = userRepository.getReferenceById(userId);
        return weightRepository.findByUserAndDateBetweenOrderByDateAsc(user, start, end);
    }

    /**
     * Produces graph-ready data for the Analytics screen with adaptive downsampling.
     *
     * Strategy:
     * - WEEK and MONTH periods always return raw data (max ~30 points).
     * - YEAR, FIVE_YEAR, LIFETIME check the record count first:
     *   - count ≤ 500 → raw data (fast for most users)
     *   - count > 500 → downsample to weekly averages
     *   - if weekly averages > 500 → downsample further to monthly averages
     *
     * Stats (totalChange, weeklyChangeRate) are always calculated from raw data
     * endpoints, not from downsampled values, to preserve accuracy.
     *
     * @param userId The user ID.
     * @param period Time period string: WEEK, MONTH, YEAR, FIVE_YEAR, or LIFETIME.
     * @return GraphDataResponse with plot points, resolution, and summary stats.
     */
    @Transactional(readOnly = true)
    public GraphDataResponse getGraphData(Long userId, String period) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start;

        switch (period.toUpperCase()) {
            case "WEEK":     start = now.minusDays(7);    break;
            case "YEAR":     start = now.minusYears(1);   break;
            case "FIVE_YEAR":start = now.minusYears(5);   break;
            case "LIFETIME": start = LocalDateTime.of(1900, 1, 1, 0, 0); break;
            default:         start = now.minusDays(30);   break; // MONTH (default)
        }

        User user = userRepository.getReferenceById(userId);
        List<WeightEntry> rawEntries = weightRepository.findByUserAndDateBetweenOrderByDateAsc(user, start, now);

        // Filter out soft-deleted tombstones
        List<WeightEntry> activeEntries = rawEntries.stream()
                .filter(e -> !Boolean.TRUE.equals(e.getIsDeleted()))
                .toList();

        int totalRecords = activeEntries.size();

        // Determine resolution and build data points
        List<GraphDataPoint> dataPoints;
        String resolution;

        if (totalRecords <= 500 || "WEEK".equalsIgnoreCase(period) || "MONTH".equalsIgnoreCase(period)) {
            // Return raw data directly
            dataPoints = activeEntries.stream()
                    .map(e -> GraphDataPoint.builder().date(e.getDate()).weight(e.getWeight()).build())
                    .toList();
            resolution = "RAW";
        } else {
            // Downsample to weekly averages
            dataPoints = downsampleToWeekly(activeEntries);
            resolution = "WEEKLY";

            // If weekly still exceeds 500, downsample to monthly
            if (dataPoints.size() > 500) {
                dataPoints = downsampleToMonthly(dataPoints);
                resolution = "MONTHLY";
            }
        }

        // Calculate summary stats from raw data (not downsampled)
        Double totalChange = null;
        Double weeklyChangeRate = null;

        if (activeEntries.size() >= 2) {
            WeightEntry first = activeEntries.getFirst();
            WeightEntry last = activeEntries.getLast();
            totalChange = last.getWeight() - first.getWeight();

            long daysDiff = ChronoUnit.DAYS.between(first.getDate(), last.getDate());
            double weeksDiff = Math.max(daysDiff / 7.0, 1.0);
            weeklyChangeRate = totalChange / weeksDiff;
        }

        return GraphDataResponse.builder()
                .dataPoints(dataPoints)
                .resolution(resolution)
                .totalRecords(totalRecords)
                .totalChange(totalChange)
                .weeklyChangeRate(weeklyChangeRate)
                .build();
    }

    /**
     * Downsamples raw weight entries to ISO-week averages.
     * Groups entries by year + ISO week number, calculates the average weight
     * for each group, and uses the group's midpoint date.
     *
     * @param entries Raw weight entries (chronological order).
     * @return Weekly-averaged data points, sorted chronologically.
     */
    private List<GraphDataPoint> downsampleToWeekly(List<WeightEntry> entries) {
        // Group by year + ISO week number (e.g., "2026-W07")
        Map<String, List<WeightEntry>> grouped = entries.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getDate().get(IsoFields.WEEK_BASED_YEAR)
                                + "-W" + e.getDate().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return grouped.values().stream()
                .map(group -> {
                    double avgWeight = group.stream()
                            .mapToDouble(WeightEntry::getWeight)
                            .average()
                            .orElse(0.0);
                    // Use midpoint date of the group
                    LocalDateTime midDate = group.get(group.size() / 2).getDate();
                    return GraphDataPoint.builder()
                            .date(midDate)
                            .weight(Math.round(avgWeight * 10.0) / 10.0)
                            .build();
                })
                .toList();
    }

    /**
     * Downsamples weekly data points to calendar-month averages.
     * Used when weekly downsampling still produces > 500 points (extreme datasets).
     *
     * @param weeklyPoints Weekly-averaged data points.
     * @return Monthly-averaged data points, sorted chronologically.
     */
    private List<GraphDataPoint> downsampleToMonthly(List<GraphDataPoint> weeklyPoints) {
        Map<YearMonth, List<GraphDataPoint>> grouped = weeklyPoints.stream()
                .collect(Collectors.groupingBy(
                        p -> YearMonth.from(p.getDate()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return grouped.values().stream()
                .map(group -> {
                    double avgWeight = group.stream()
                            .mapToDouble(GraphDataPoint::getWeight)
                            .average()
                            .orElse(0.0);
                    LocalDateTime midDate = group.get(group.size() / 2).getDate();
                    return GraphDataPoint.builder()
                            .date(midDate)
                            .weight(Math.round(avgWeight * 10.0) / 10.0)
                            .build();
                })
                .toList();
    }

    /**
     * Generates a CSV export of the user's entire weight history.
     * Generated server-side to avoid the "N+1 Query" problem of the mobile app downloading
     * thousands of JSON objects just to save a simple text file.
     *
     * @param userId The User ID.
     * @return A byte array representing the CSV file content.
     */
    @Transactional(readOnly = true)
    public byte[] generateWeightHistoryCsv(Long userId) {
        User user = userRepository.getReferenceById(userId);

        // Fetch ALL data (Using range 1900 to 2100 to simulate "All Time" without adding new Repo methods)
        LocalDateTime start = LocalDateTime.of(1900, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2112, 1, 1, 0, 0);
        List<WeightEntry> entries = weightRepository.findByUserAndDateBetweenOrderByDateAsc(user, start, end);

        StringBuilder csvBuilder = new StringBuilder();
        csvBuilder.append("Date,Weight (lbs)\n"); // Header

        for (WeightEntry entry : entries) {
            // Format: 2026-01-16T10:15:30,150.5
            csvBuilder.append(entry.getDate().toString())
                    .append(",")
                    .append(entry.getWeight())
                    .append("\n");
        }

        return csvBuilder.toString().getBytes(StandardCharsets.UTF_8);
    }

    // --- ANALYTICS ---

    /**
     * Calculates comprehensive statistics for ANY date range.
     * This avoids hardcoding "1 Week" or "1 Month" logic.
     *
     * @param userId The User ID.
     * @param start  Range start.
     * @param end    Range end.
     * @return A DTO containing Min, Max, Change, Slope, etc.
     */
    @Transactional(readOnly = true)
    public WeightStats calculateStatistics(Long userId, LocalDateTime start, LocalDateTime end) {
        // Fetch Raw Data (Reuse existing logic)
        List<WeightEntry> entries = getWeightsForGraph(userId, start, end);

        // Handle "No Data" Case
        if (entries.isEmpty()) {
            return WeightStats.builder()
                    .rangeStartDate(start).rangeEndDate(end)
                    .totalLogs(0).totalChange(0.0).weeklyChangeRate(0.0)
                    .build();
        }

        // Descriptive Stats (Min/Max)
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;

        for (WeightEntry entry : entries) {
            if (entry.getWeight() < min) min = entry.getWeight();
            if (entry.getWeight() > max) max = entry.getWeight();
        }

        // Change Analysis
        WeightEntry first = entries.getFirst();
        WeightEntry last = entries.getLast();

        double totalChange = last.getWeight() - first.getWeight();

        // Slope Calculation (Weekly Rate)
        long daysDiff = ChronoUnit.DAYS.between(first.getDate(), last.getDate());
        double weeksDiff = Math.max(daysDiff / 7.0, 1.0); // Guard against divide by zero
        double weeklyRate = totalChange / weeksDiff;

        return WeightStats.builder()
                .rangeStartDate(start)
                .rangeEndDate(end)
                .startingWeight(first.getWeight())
                .endingWeight(last.getWeight())
                .minWeight(min)
                .maxWeight(max)
                .totalLogs(entries.size())
                .totalChange(totalChange)
                .weeklyChangeRate(weeklyRate)
                .build();
    }

    // --- DELETING DATA ---

    /**
     * Performs a SOFT DELETE (Tombstone).
     * We do not remove the row. We mark it isDeleted=true.
     * This ensures the Android app knows to delete it locally during the next sync.
     *
     * @param weightId The ID of the entry to delete.
     */
    @Transactional
    public void deleteWeightEntry(UUID weightId) {
        WeightEntry entry = weightRepository.findById(weightId)
                .orElseThrow(() -> new IllegalArgumentException("Weight entry not found"));

        // 1. Mark as deleted (Tombstone)
        entry.setIsDeleted(true);
        weightRepository.save(entry);

        // 2. Cache Recalculation Check
        // If we just deleted the "Current Weight", we need to find the previous one
        // to update the dashboard.
        updateCurrentWeightCache(entry.getUser());
    }

    /**
     * Recalculates the user's currentWeight cache from the most recent active entry.
     * Filters out soft-deleted tombstones and uses the date_recorded field (user's measurement date)
     * to determine which entry is truly the most recent.
     * Called after both additions (handles back-dated entries) and deletions.
     */
    private void updateCurrentWeightCache(User user) {
        Optional<WeightEntry> latest = weightRepository.findTopByUserAndIsDeletedFalseOrderByDateDesc(user);

        if (latest.isPresent()) {
            user.setCurrentWeight(latest.get().getWeight());
        } else {
            user.setCurrentWeight(null); // No active weights left
        }
        userRepository.save(user);
    }

    // --- DELTA SYNC (P0: Offline-First Synchronization) ---

    /**
     * Retrieves weight entries modified since a specific timestamp.
     * Used by the Android sync endpoint for delta synchronization.
     *
     * This method:
     * 1. Fetches all records with updatedAt > since (includes tombstones)
     * 2. Maps entities to DTOs with sync fields (createdAt, updatedAt)
     * 3. Returns a DeltaSyncResponse with serverTimestamp for next sync
     *
     * @param userId   The user ID.
     * @param since    Timestamp from client's last successful sync.
     * @param pageable Pagination info (page size, typically 50).
     * @return DeltaSyncResponse containing changed entries and metadata.
     */
    @Transactional(readOnly = true)
    public DeltaSyncResponse getDeltaSync(Long userId, LocalDateTime since, Pageable pageable) {
        User user = userRepository.getReferenceById(userId);

        Slice<WeightEntry> slice = weightRepository
                .findByUserAndUpdatedAtAfterOrderByUpdatedAtAsc(user, since, pageable);

        List<WeightEntryResponse> entries = slice.getContent().stream()
                .map(this::mapToSyncResponse)
                .toList();

        return DeltaSyncResponse.builder()
                .entries(entries)
                .serverTimestamp(LocalDateTime.now())
                .hasMore(slice.hasNext())
                .build();
    }

    /**
     * Maps a WeightEntry entity to a WeightEntryResponse DTO with sync fields.
     * Includes createdAt and updatedAt for client sync tracking.
     */
    private WeightEntryResponse mapToSyncResponse(WeightEntry entry) {
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