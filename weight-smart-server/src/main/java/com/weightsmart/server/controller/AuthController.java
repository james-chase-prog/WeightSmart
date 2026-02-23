package com.weightsmart.server.controller;

import com.weightsmart.server.util.JwtUtil;
import com.weightsmart.server.dto.AuthResponse;
import com.weightsmart.server.dto.LoginRequest;
import com.weightsmart.server.dto.RefreshTokenRequest;
import com.weightsmart.server.dto.RegisterRequest;
import com.weightsmart.server.dto.UserResponse;
import com.weightsmart.server.dto.UpdateProfileRequest;
import com.weightsmart.server.model.User;
import com.weightsmart.server.service.RateLimiterService;
import com.weightsmart.server.service.TokenBlacklistService;
import com.weightsmart.server.service.TrieService;
import com.weightsmart.server.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/*
 * AuthController
 * The public entry point for User Identity operations under /api/auth/** :
 * 1) POST /api/auth/register -> Creates a new user and updates the Search Trie.
 * 2) POST /api/auth/login    -> Verifies credentials and issues a JWT.
 * 3) GET  /api/auth/search-usernames -> Type-ahead search (Protected by Rate Limiter).
 * 4) PUT  /api/auth/update   -> Updates the authenticated user's profile.
 * 5) GET  /api/auth/me       -> Retrieves the fresh profile of the currently logged-in user.
 * 6) GET  /api/auth/validate -> Lightweight check to confirm the current JWT is still valid.
 * 7) POST /api/auth/refresh  -> Exchanges a refresh token for a new access token (P5). Rate limited.
 * 8) POST /api/auth/logout   -> Blacklists access + refresh tokens (P5).
 *
 * REQUEST LIFECYCLE QUICK MAP
 * 1) Register:
 * Controller -> RateLimiter(Auth) -> UserService.registerUser() -> DB Save
 * -> TrieService.insert() (Cache Update)
 * -> JwtUtil.generateToken() + generateRefreshToken() -> return Response
 * 2) Search:
 * Controller -> RateLimiterService (Check IP) -> TrieService.search() -> return List<String>
 *
 * Architecture Role:
 * 1. Gateway: Validates HTTP inputs before passing to Service layer.
 * 2. Security Boundary: Enforces Rate Limiting (Token Bucket) on public endpoints.
 * 3. Event Orchestrator: Ensures that when a user registers, the Search Index (Trie) is updated.
 *
 * Key Concepts & Documentation:
 * <a href="https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-restcontroller.html">@RestController</a>:
 * Tells Spring this class handles HTTP requests and returns JSON.
 * Token Bucket Algorithm:
 * Used in 'searchUsernames' to prevent Enumeration Attacks by limiting requests per IP.
 *
 * @author James Chase
 * @version 3.0 (P5: Rate limiting, refresh tokens, logout, IP validation, error sanitization)
 * @since 2026-01-31
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    // Services for Search, Security & Token Management
    private final TrieService trieService;
    private final RateLimiterService rateLimiterService;
    private final TokenBlacklistService tokenBlacklistService;

    // IP validation pattern: IPv4 and IPv6
    private static final Pattern IP_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$" +
            "|^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$"
    );

    @Autowired
    public AuthController(AuthenticationManager authenticationManager,
                          UserService userService,
                          JwtUtil jwtUtil,
                          UserDetailsService userDetailsService,
                          TrieService trieService,
                          RateLimiterService rateLimiterService,
                          TokenBlacklistService tokenBlacklistService) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.trieService = trieService;
        this.rateLimiterService = rateLimiterService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    /**
     * Endpoint: REGISTER a new user.
     * URL: POST /api/auth/register
     * Rate limited: 5 requests/minute per IP (auth bucket).
     *
     * @param request The JSON body containing name, email, password, etc.
     * @param httpRequest The HTTP request for IP extraction.
     * @return AuthResponse containing the new JWT, refresh token, and the safe User profile.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request,
                                      HttpServletRequest httpRequest) {
        // Rate limit registration to prevent mass account creation
        String clientIp = getClientIp(httpRequest);
        if (!rateLimiterService.tryConsumeAuth(clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Too many attempts. Try again later."));
        }

        User newUser = new User();
        newUser.setUsername(request.getUsername());
        newUser.setEmail(request.getEmail());
        newUser.setPassword(request.getPassword()); // Service will hash this
        newUser.setAge(request.getAge());
        newUser.setHeight(request.getHeight());
        newUser.setCurrentWeight(request.getCurrentWeight());
        newUser.setGoalWeight(request.getGoalWeight());
        newUser.setTargetDate(request.getTargetDate());
        if (request.getNickname() != null) newUser.setNickname(request.getNickname());

        // Create User in Postgres
        User savedUser = userService.registerUser(newUser);

        // Mint Tokens (access + refresh)
        UserDetails userDetails = userDetailsService.loadUserByUsername(savedUser.getUsername());
        String token = jwtUtil.generateToken(userDetails);
        String refreshToken = jwtUtil.generateRefreshToken(userDetails);

        return ResponseEntity.ok(new AuthResponse(token, refreshToken, mapToUserResponse(savedUser)));
    }

    /**
     * Endpoint: LOGIN an existing user.
     * URL: POST /api/auth/login
     * Rate limited: 5 requests/minute per IP (auth bucket).
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletRequest httpRequest) {
        // Rate limit login to prevent brute-force attacks
        String clientIp = getClientIp(httpRequest);
        if (!rateLimiterService.tryConsumeAuth(clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Too many attempts. Try again later."));
        }

        // Authenticate (Checks Password & Locking Logic)
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getUsername());
        User user = userService.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        // Mint Tokens (access + refresh)
        String token = jwtUtil.generateToken(userDetails);
        String refreshToken = jwtUtil.generateRefreshToken(userDetails);
        return ResponseEntity.ok(new AuthResponse(token, refreshToken, mapToUserResponse(user)));
    }

    /**
     * Endpoint: REFRESH an access token using a refresh token.
     * URL: POST /api/auth/refresh
     * Public endpoint (no JWT required, but requires a valid refresh token).
     * Rate limited: 5 requests/minute per IP (auth bucket) to prevent token minting abuse.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody RefreshTokenRequest request,
                                          HttpServletRequest httpRequest) {
        // Rate limit refresh to prevent rapid token minting from a compromised refresh token
        String clientIp = getClientIp(httpRequest);
        if (!rateLimiterService.tryConsumeAuth(clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Too many attempts. Try again later."));
        }

        String refreshToken = request.getRefreshToken();
        try {
            String username = jwtUtil.extractUsername(refreshToken);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            if (!jwtUtil.isTokenValid(refreshToken, userDetails) || !jwtUtil.isRefreshToken(refreshToken)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid refresh token"));
            }

            // Check if refresh token has been blacklisted
            if (tokenBlacklistService.isBlacklisted(refreshToken)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid refresh token"));
            }

            String newAccessToken = jwtUtil.generateToken(userDetails);
            return ResponseEntity.ok(Map.of("token", newAccessToken));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid refresh token"));
        }
    }

    /**
     * Endpoint: LOGOUT (blacklist access and refresh tokens).
     * URL: POST /api/auth/logout
     * Requires authentication. Blacklists the access token from the Authorization header
     * and the refresh token from the request body (if provided).
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request,
                                       @RequestBody(required = false) RefreshTokenRequest body) {
        // Blacklist the access token from Authorization header
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            tokenBlacklistService.blacklist(authHeader.substring(7));
        }
        // Blacklist the refresh token from request body (prevents minting new access tokens)
        if (body != null && body.getRefreshToken() != null && !body.getRefreshToken().isBlank()) {
            tokenBlacklistService.blacklist(body.getRefreshToken());
        }
        return ResponseEntity.ok().build();
    }

    /**
     * Endpoint: SEARCH USERNAMES (Type-ahead).
     * URL: GET /api/auth/search-usernames?prefix=jam
     *
     * Protected by:
     * 1. Token Bucket Rate Limiter (Anti-Scraping)
     * 2. Max Length Guard (Anti-DoS)
     */
    @GetMapping("/search-usernames")
    public ResponseEntity<?> searchUsernames(@RequestParam String prefix, HttpServletRequest request) {
        // SECURITY: Rate Limiting
        // Prevents automation attacks.
        String clientIp = getClientIp(request);
        if (!rateLimiterService.tryConsume(clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Rate limit exceeded. Try again later.");
        }

        // SECURITY: Max-Depth Guard
        // Prevents excessive memory usage in Trie traversal.
        if (prefix.length() > 20) {
            return ResponseEntity.badRequest()
                    .body("Search query too long. Max length is 20 characters.");
        }

        // VALIDATION: Min Length
        // Don't search for single characters (Performance).
        if (prefix == null || prefix.length() < 2) {
            return ResponseEntity.ok(List.of());
        }

        // EXECUTION
        List<String> matches = trieService.search(prefix);
        return ResponseEntity.ok(matches);
    }

    /**
     * Endpoint: UPDATE PROFILE details.
     * URL: PUT /api/auth/update
     */
    @PutMapping("/update")
    public ResponseEntity<UserResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userService.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Long userId = currentUser.getId();

        User updates = new User();
        updates.setNickname(request.getNickname());
        if (request.getAge() != null) updates.setAge(request.getAge());
        updates.setHeight(request.getHeight());
        updates.setGoalWeight(request.getGoalWeight());
        updates.setTargetDate(request.getTargetDate());

        User result = userService.updateProfile(userId, updates);
        return ResponseEntity.ok(mapToUserResponse(result));
    }

    /**
     * Endpoint: GET CURRENT USER details.
     * URL: GET /api/auth/me
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(mapToUserResponse(user));
    }

    /**
     * Endpoint: VALIDATE TOKEN validity.
     * URL: GET /api/auth/validate
     */
    @GetMapping("/validate")
    public ResponseEntity<Void> validateToken() {
        return ResponseEntity.ok().build();
    }

    // --- HELPER METHODS ---

    /**
     * Helper Method: Maps the Database Entity to the Safe DTO.
     */
    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .age(user.getAge())
                .height(user.getHeight())
                .currentWeight(user.getCurrentWeight())
                .goalWeight(user.getGoalWeight())
                .targetDate(user.getTargetDate())
                .role(user.getRole())
                .build();
    }

    /**
     * Helper Method: Extracts the client IP address with validation.
     * Handles cases where the server is behind a proxy (Load Balancer/Nginx).
     * Validates X-Forwarded-For format to prevent IP spoofing for rate limit bypass.
     */
    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null) {
            // X-Forwarded-For: client, proxy1, proxy2 -- take the first (client) IP
            String candidate = xfHeader.split(",")[0].trim();
            if (IP_PATTERN.matcher(candidate).matches()) {
                return candidate;
            }
            // Invalid format -- fall through to remoteAddr (prevents rate limit bypass)
        }
        return request.getRemoteAddr();
    }
}
