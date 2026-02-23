package com.weightsmart.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/*
 * GraphDataPoint
 * Represents a single data point for the weight trend graph.
 *
 * Architecture Role:
 * Lightweight DTO used in GraphDataResponse.dataPoints. Each point maps to either
 * a raw weight entry or a downsampled average (weekly/monthly) depending on the
 * dataset size and requested time period.
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-02-18
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GraphDataPoint {

    /** The date/time for this data point (raw entry date or group midpoint for downsampled). */
    private LocalDateTime date;

    /** Weight value in lbs (raw entry weight or group average for downsampled). */
    private Double weight;
}
