package com.weightsmart.server.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/*
 * RefreshTokenRequest DTO
 * The payload sent by the client to exchange a refresh token for a new access token.
 *
 * Architecture Role:
 * Used by POST /api/auth/refresh. The client sends the long-lived refresh token
 * (received during login/register) to obtain a new short-lived access token
 * without requiring re-authentication.
 *
 * @author James Chase
 * @version 1.0 (P5: JWT refresh token mechanism)
 * @since 2026-02-14
 */
@Data
public class RefreshTokenRequest {

    // The refresh JWT previously issued during login/register
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
