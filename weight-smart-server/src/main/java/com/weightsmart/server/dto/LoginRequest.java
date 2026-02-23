package com.weightsmart.server.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/*
 * LoginRequest DTO
 * Simple data carrier for authentication attempts from client to server.
 *
 * Architecture Role:
 * Defines the minimal JSON payload required for the POST /api/auth/login endpoint.
 * By accepting only username and password, we prevent injection of fields like "role" or "id"
 * during the login flow.
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-01-14
 */
@Data
public class LoginRequest {

    // The user's unique login identifier. @NotBlank prevents empty or whitespace-only values.
    @NotBlank(message = "Username cannot be empty")
    private String username;

    // The plaintext password to verify against the stored BCrypt hash.
    @NotBlank(message = "Password cannot be empty")
    private String password;
}