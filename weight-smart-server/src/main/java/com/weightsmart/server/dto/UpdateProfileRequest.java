package com.weightsmart.server.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDate;


/*
 * UpdateProfileRequest DTO
 * The payload for modifying non-sensitive user profile details from client to server.
 *
 * Architecture Role:
 * Allows users to update their physical stats (Age, Height) and application goals
 * (Goal Weight, Nickname) without exposing sensitive authentication fields like
 * Password or Email. This separation ensures the "Update Profile" endpoint cannot
 * be exploited to take over an account.
 *
 * Key Concepts & Documentation:
 * Nullable Wrapper Types: Uses Integer/Double (not int/double) so null means "don't update this field",
 * enabling partial updates without requiring all fields in every request.
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-01-14
 */
@Data
public class UpdateProfileRequest {

    // User's age. @Min(13) enforces COPPA compliance. Null means "no change".
    @Min(value = 13, message = "Invalid age")
    @Max(value = 120, message = "Invalid age")
    private Integer age;

    // User's height in inches. Null means "no change".
    @Min(value = 1, message = "Height must be positive")
    private Double height;

    // Target weight the user is working towards. Null means "no change".
    @Min(value = 1, message = "Goal weight must be positive")
    private Double goalWeight;

    // Optional display name. @Size(max=20) prevents Trie exploitation.
    @Size(max = 20, message ="Nickname too long")
    @Pattern(regexp = "^[A-Za-z]+$", message = "Nickname can only contain letters.")
    private String nickname;

    // Target date for reaching goal weight. Null means "no change".
    private LocalDate targetDate;
}