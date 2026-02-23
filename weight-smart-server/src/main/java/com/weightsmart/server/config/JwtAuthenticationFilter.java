package com.weightsmart.server.config;

import com.weightsmart.server.service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.weightsmart.server.util.JwtUtil;

import java.io.IOException;

/*
 * JwtAuthenticationFilter
 * The "Bouncer" that intercepts every HTTP request to check for a valid ID Card (JWT).
 *
 * Architecture Role:
 * This filter sits in the Spring Security Filter Chain. Running once per HTTP request, it executes BEFORE the request reaches
 * your Controllers. It checks for the authorization header: "Bearer <token> and If a valid token is found,
 * it manually logs the user in for the duration of that single request by loading:
 * 1) Loading  UserDetails from DB
 * 2) Creating Authentication object
 * 3) Storing the object in SecurityContextHolder
 *
 * P5 Addition: Checks the TokenBlacklistService before authenticating. If a token has been
 * blacklisted (via /logout), the request proceeds anonymously (will be rejected by SecurityConfig).
 *
 * This is required for stateless API config as every request must reprove identity via the JWT.
 *
 * Key Concepts & Documentation:
 * OncePerRequestFilter: Guarantees this logic runs exactly once per request.
 * <a href="https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/filter/OncePerRequestFilter.html">Reference: OncePerRequestFilter</a>
 * SecurityContextHolder: The "Global Storage" where Spring keeps the details of the currently logged-in user.
 * <a href="https://docs.spring.io/spring-security/site/docs/4.0.x/apidocs/org/springframework/security/core/context/SecurityContextHolder.html">Reference: SecurityContextHolder</a>
 * Bearer Token: The standard HTTP format for sending tokens ("Authorization: Bearer <token>").
 * <a href="https://oauth.net/2/bearer-tokens/">Reference: OAuth 2.0 Bearer Token Usage</a>
 *
 * @author James Chase
 * @version 2.0 (P5: Token blacklist check)
 * @since 2026-01-17
 */

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    @Autowired
    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                   UserDetailsService userDetailsService,
                                   TokenBlacklistService tokenBlacklistService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    /**
     * The core logic loop.
     * 1. Check for "Authorization" header.
     * 2. Extract Token.
     * 3. Check if token is blacklisted (logged out).
     * 4. Validate Token.
     * 5. Set "Authenticated User" in Spring Context.
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        String username = null;

        // Early Exit
        // If Users does not have a token assigned (no Bearer prefix) then they are not authorized
        // By allowing to proceed unauthorized here, the request proceeds to SecurityConfig where it will be rejected
        // with proper error handling. (401/403)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract the token (Remove "Bearer " prefix)
        jwt = authHeader.substring(7);

        // P5: Check if token has been blacklisted (user logged out)
        if (tokenBlacklistService.isBlacklisted(jwt)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract Username (This verifies the signature automatically)
        // If signature is invalid, this throws an exception and request stops here.
        try {
            username = jwtUtil.extractUsername(jwt);
        } catch (Exception e) {
            // If token is expired/invalid, we just ignore it and let the request
            // proceed anonymously. SecurityConfig will block it later with a 403.
            // This prevents the 500 crash.
            username = null;
        }

        // If User is found but not yet authenticated in this Context...
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // Load full user details from DB verifying user is still active
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

            // Final Validation check
            if (jwtUtil.isTokenValid(jwt, userDetails)) {

                // Create the "Authentication Ticket"
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );

                // Add request details (IP address, Session ID) to the ticket
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // STAMP THE TICKET: Tell Spring Security "This user is officially logged in"
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // Continue the chain (Go to the next filter or the Controller)
        filterChain.doFilter(request, response);
    }
}
