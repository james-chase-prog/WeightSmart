package com.weightsmart.server.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import javax.crypto.SecretKey;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/*
 * JwtUtil
 * The "Mint" and "Verifier" for our security tokens.
 *
 * Architecture Role:
 * 1. Minting: Creates a signed JSON Web Token (JWT) when a user logs in.
 * 2. Verifying: Checks if a token sent by the Android app is valid and unexpired.
 * 3. Extraction: Pulls the username out of the token so we know who is asking.
 *
 * Security Note:
 * We use HMAC-SHA256 (HS256) encryption. The security of the entire system relies
 * on the secret key. In production, this is injected via environment variables
 * through Spring profiles (application-prod.properties).
 *
 * Key Concepts & Documentation:
 * JWT (JSON Web Token): A compact, URL-safe means of representing claims to be transferred between two parties.
 * <a href="https://jwt.io/introduction">Reference: JWT Introduction</a>
 * JJWT Library (0.13.0): The modern Java library used to create and verify these tokens.
 * <a href="https://github.com/jwtk/jjwt">Reference: JJWT GitHub Documentation</a>
 * HMAC-SHA256: The symmetric encryption algorithm used to sign the tokens. Relies on a shared secret key.
 * <a href="https://en.wikipedia.org/wiki/HMAC">Reference: HMAC Algorithm</a>
 *
 * @author James Chase
 * @version 3.0 ( Secrets externalized, refresh token support)
 * @since 2026-01-17
 */

@Service
public class JwtUtil {

    // Injected from application-{profile}.properties (dev: hardcoded, prod: env var)
    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration-ms:86400000}")
    private long expirationTime;

    // Refresh token expiration: 7 days default, configurable per profile
    @Value("${jwt.refresh-expiration-ms:604800000}")
    private long refreshExpiration;

    // --- GENERATE TOKEN (The "Mint") ---

    /**
     * Generates a new access JWT for the authenticated user.
     * @param userDetails The Spring Security user object.
     * @return A URL-safe String token.
     */
    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    /**
     * Generates a JWT with optional extra claims (e.g., roles, permissions).
     * The token is signed with HMAC-SHA256 using the application's secret key.
     *
     * @param extraClaims Additional key-value pairs to embed in the token payload.
     * @param userDetails The Spring Security user object (username becomes the "subject" claim).
     * @return A compact, URL-safe JWT string.
     */
    private String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return Jwts.builder()
                .claims(extraClaims) // Custom data (roles, permissions)
                .subject(userDetails.getUsername())  // Links token to username as identifier
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(getSignInKey(), Jwts.SIG.HS256) // Uses SecretKey to enforce type safety
                .compact();
    }

    /**
     * Generates a long-lived refresh token (7 days).
     * Contains a "type": "refresh" claim to distinguish from access tokens.
     * Used to obtain new access tokens without re-authenticating.
     *
     * @param userDetails The Spring Security user object.
     * @return A compact, URL-safe refresh JWT string.
     */
    public String generateRefreshToken(UserDetails userDetails) {
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("type", "refresh")
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(getSignInKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Checks whether a token is a refresh token (has "type": "refresh" claim).
     *
     * @param token The JWT string to check.
     * @return true if the token contains the refresh type claim.
     */
    public boolean isRefreshToken(String token) {
        try {
            return "refresh".equals(extractClaim(token, c -> c.get("type", String.class)));
        } catch (Exception e) {
            return false;
        }
    }

    // --- VALIDATE TOKEN (The "Verifier") ---

    /**
     * Validates a JWT against the provided user details.
     * A token is valid if: (1) the embedded username matches, AND (2) the token has not expired.
     *
     * @param token The JWT string to validate.
     * @param userDetails The authenticated user to compare against.
     * @return true if the token is valid for this user, false otherwise.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        // Valid if: Username matches AND Token is not expired
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    /**
     * Checks if the token's expiration date is in the past.
     *
     * @param token The JWT string to check.
     * @return true if the token has expired.
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Extracts the expiration date from a token.
     * Used by TokenBlacklistService for cleanup of naturally expired tokens.
     *
     * @param token The JWT string.
     * @return The Date when this token expires, or null if unparseable.
     */
    public Date getExpiration(String token) {
        try {
            return extractExpiration(token);
        } catch (Exception e) {
            return null;
        }
    }

    // --- EXTRACT DATA (The "Reader") ---

    /**
     * Extracts the username (subject claim) from the token.
     *
     * @param token The JWT string.
     * @return The username embedded in the token's "sub" claim.
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts the expiration date from the token.
     *
     * @param token The JWT string.
     * @return The Date when this token expires.
     */
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Generic claim extractor using a function reference.
     * Parses the token once and applies the resolver to retrieve any specific claim.
     *
     * @param token The JWT string.
     * @param claimsResolver A function that extracts the desired claim (e.g., Claims::getSubject).
     * @param <T> The return type of the claim.
     * @return The extracted claim value.
     */
    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Parses the signed JWT. This will THROW an exception if the signature is invalid
     * (meaning the token was tampered with).
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Decodes the hex-encoded secret key string into a cryptographic SecretKey object.
     * Used by both token generation (signing) and token validation (verification).
     *
     * @return The HMAC-SHA256 SecretKey for signing and verifying JWTs.
     */
    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
