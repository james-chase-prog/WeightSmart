package com.weightsmart.server.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/*
 * WeightStats DTO
 * Container for aggregated descriptive statistics calculated server-side for a given date range.
 *
 * Architecture Role:
 * Replaces sending thousands of raw data points to the client for simple dashboard metrics.
 * The server calculates Min, Max, Total Change, and Weekly Rate in a single query pass,
 * and returns this lightweight summary to the Android Analytics screen.
 *
 * Key Concepts & Documentation:
 * Server-Side Aggregation: Performing calculations on the server avoids transferring large datasets
 * over mobile networks. The client requests stats for a date range and receives pre-computed metrics.
 * Builder Pattern: Lombok's @Builder enables clean construction in WeightService.calculateStatistics().
 * <a href="https://projectlombok.org/features/Builder">Reference: Lombok @Builder</a>
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-01-17
 */
@Data
@Builder
public class WeightStats {

    // --- Range Definition ---

    // Start of the analysis window (inclusive). Parsed from ISO-8601 query param.
    private LocalDateTime rangeStartDate;

    // End of the analysis window (inclusive). Parsed from ISO-8601 query param.
    private LocalDateTime rangeEndDate;

    // --- Descriptive Stats ---

    // First weight entry in the range (chronological order).
    private Double startingWeight;

    // Last weight entry in the range (chronological order)
    private Double endingWeight;

    // Lowest weight value recorded in the range
    private Double minWeight;

    // Highest weight value recorded in the range
    private Double maxWeight;

    // Total number of weight entries in the range
    private int totalLogs;

    // --- Analysis ---

    // Net weight change (endingWeight - startingWeight). Positive = gained, negative = lost.
    private Double totalChange;

    // Rate of change per week (totalChange / weeks). The "slope" of the weight trend line
    private Double weeklyChangeRate;
}