package com.weightsmart.server.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/*
 * GraphDataResponse
 * Container for graph visualization data returned by the /graph endpoint.
 *
 * Architecture Role:
 * Wraps plot data with metadata about resolution and summary statistics.
 * The Android client uses dataPoints for MPAndroidChart rendering, resolution
 * for adaptive moving average windows, and stats for the dashboard cards.
 *
 * Downsampling Strategy:
 * - RAW: All data points returned as-is (for small datasets or short periods).
 * - WEEKLY: ISO-week averages (when raw count > 500, reduces to ~52 pts/year).
 * - MONTHLY: Calendar month averages (when weekly count > 500).
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-02-18
 */
@Data
@Builder
public class GraphDataResponse {

    /** Plot data points (raw entries or downsampled averages). */
    private List<GraphDataPoint> dataPoints;

    /** Resolution indicator: "RAW", "WEEKLY", or "MONTHLY". */
    private String resolution;

    /** Original record count before downsampling. */
    private int totalRecords;

    /** Net weight change (endingWeight - startingWeight). Null if no data. */
    private Double totalChange;

    /** Rate of change per week (totalChange / weeks). Null if no data. */
    private Double weeklyChangeRate;
}
