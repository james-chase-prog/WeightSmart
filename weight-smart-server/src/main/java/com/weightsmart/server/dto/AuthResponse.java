package com.weightsmart.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/*
 * AuthResponse DTO
 * The payload returned to the client after a successful Login or Registration.
 *
 * Architecture Role:
 * 1. Token Delivery: Contains the JWT (Key) the client must send in the Authorization header
 *    for all future authenticated requests.
 * 2. Refresh Token: Contains a long-lived refresh token for obtaining new access tokens
 *    without re-authentication (P5: JWT hardening).
 * 3. Eager Profile Load: Bundles the full UserResponse profile so the Android app can populate
 *    the dashboard immediately without a second network call.
 *
 * Key Concepts & Documentation:
 * JWT Bearer Token: The token string is sent as "Authorization: Bearer {token}" in subsequent requests.
 * <a href="https://oauth.net/2/bearer-tokens/">Reference: OAuth 2.0 Bearer Token Usage</a>
 *
 * @author James Chase
 * @version 2.0 (P5: Added refresh token)
 * @since 2026-01-14
 */
@Data
@AllArgsConstructor
public class AuthResponse {

    // The signed access JWT token string. Client stores this and sends it in every request header.
    private String token;

    // Long-lived refresh token (7 days). Used to obtain new access tokens via POST /api/auth/refresh.
    private String refreshToken;

    // Safe user profile data (excludes password hash, lock timers, internal flags).
    private UserResponse user;
}
