package com.weightsmart.server.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;

/*
 * UserResponse DTO
 * A "Safe View" of the user profile sent to the client.
 *
 * Architecture Role:
 * Strips sensitive internal fields (password hash, failedLoginAttempts, lockTime, deletedAt)
 * from the User entity before serialization to JSON. This prevents data leakage through
 * the API response and decouples the public contract from the internal database schema.
 *
 * Key Concepts & Documentation:
 * Builder Pattern: Lombok's @Builder generates a fluent API for constructing instances,
 * making the mapToUserResponse() helper methods in controllers clean and readable.
 * <a href="https://projectlombok.org/features/Builder">Reference: Lombok @Builder</a>
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-01-14
 */
@Data
@Builder
public class UserResponse {

    // Database primary key. Exposed for client-side caching and API calls requiring userId.
    private Long id;

    // Unique login identifier (immutable after registration)
    private String username;

    // User's email address (used for account recovery, notifications)
    private String email;

    // Optional display name shown on the dashboard instead of username.
    private String nickname;

    // User's age (used for BMI/health context).
    private int age;

    // User's height in inches (used for BMI calculation).
    private Double height;

    // Most recent weight entry value (cached on User entity for dashboard display)
    private Double currentWeight;

    // Target weight the user is working towards.
    private Double goalWeight;

    // Target date for reaching goal weight.
    private LocalDate targetDate;

    // RBAC role string (e.g., "ROLE_USER", "ROLE_ADMIN").
    private String role;
}