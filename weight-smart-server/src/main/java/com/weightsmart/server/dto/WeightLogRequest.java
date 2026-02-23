package com.weightsmart.server.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * WeightLogRequest DTO
 * The payload sent when a user adds a new weight entry.
 *
 * Architecture Role:
 * Acts as a Data Transfer Object (DTO) for the "Add Weight" API endpoint.
 * separating the internal Entity structure from the public API contract.
 *
 *  Key Concepts & Documentation:
 *  Data Transfer Object (DTO): Simple objects used to transfer data between software application subsystems.
 *  <a href="https://martinfowler.com/eaaCatalog/dataTransferObject.html">Reference: Martin Fowler DTO</a>
 *  Jakarta Validation: Enforces constraints (@NotNull, @Min) before the data reaches the service layer.
 *  <a href="https://beanvalidation.org/2.0/spec/">Reference: Jakarta Bean Validation</a>
 */
@Data
public class WeightLogRequest {

    /**
     * Optional client-generated UUID for idempotent push.
     * If provided, the server uses this ID instead of generating a new one.
     * This prevents duplicate entries when a push succeeds on the server but
     * the response is lost (e.g., network timeout) — the client retries with
     * the same UUID, and the server returns the existing entry instead of
     * creating a duplicate.
     * Nullable for backwards compatibility with clients that don't send an ID.
     */
    private UUID id;

    /**
     * The user's weight entry.
     * Constraints:
     * - @NotNull: The field must exist in the JSON body.
     * - @Min(1): Prevents unrealistic values (e.g., 0 or negative weight).
     */
    @NotNull(message = "Weight value is required")
    @Min(value = 1, message = "Weight must be positive")
    private Double weight;

    /**
     * The timestamp for the weight entry.
     * Constraints:
     * - @NotNull: The field must exist.
     * - @PastOrPresent: Ensures users cannot log weight for future dates, protecting data integrity.
     */
    @NotNull(message = "Date is required")
    @PastOrPresent(message = "Date cannot be in the future")
    private LocalDateTime date;
}