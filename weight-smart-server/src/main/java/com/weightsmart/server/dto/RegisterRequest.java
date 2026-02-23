package com.weightsmart.server.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

/*
 * RegisterRequest DTO
 * Defines the strict JSON payload required for a user to sign up.
 *
 * Architecture Role:
 * Acts as a security firewall for the POST /api/auth/register endpoint. By defining ONLY
 * these fields, we physically prevent a hacker from injecting "role": "ADMIN" or "id": 1
 * into the registration payload. Validation annotations enforce constraints before
 * the data reaches the service layer.
 *
 * Key Concepts & Documentation:
 * Jakarta Bean Validation: Constraints (@NotBlank, @Size, @Pattern, @Email, @Min, @Max) are
 * enforced automatically when @Valid is used on the controller parameter.
 * <a href="https://beanvalidation.org/2.0/spec/">Reference: Jakarta Bean Validation 2.0</a>
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-01-14
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 30, message = "Username must be 3-30 characters")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "Username can only contain letters, numbers, and underscores")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    //FIXME: Add Regix to email for email rule enforcement
    private String password;

    // --- Profile Basics (Required for onboarding) ---

    @Min(value = 13, message = "You must be at least 13 years old") //COPPA compliance
    @Max(value = 130, message = "Invalid age")
    private int age;

    @Min(value = 1, message = "Height must be valid")
    private Double height;

    @Min(value = 1, message = "Weight must be valid")
    private Double currentWeight;

    @Min(value = 1, message = "Goal weight must be valid")
    private Double goalWeight;

    @Size(max = 20, message ="Nickname too long")
    @Pattern(regexp = "^[A-Za-z]+$", message = "Nickname can only contain letters.")
    private String nickname;

    private LocalDate targetDate;
}