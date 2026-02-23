package com.weightsmart.server.service;

import com.weightsmart.server.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/*
 * TokenBlacklistService
 * In-memory token revocation list for immediate logout enforcement.
 *
 * Architecture Role:
 * When a user logs out, their JWT is added to this blacklist. The JwtAuthenticationFilter
 * checks this blacklist before authenticating any request. This prevents a stolen or
 * logged-out token from being used until its natural expiry.
 *
 * Limitations:
 * - In-memory: Server restart clears the blacklist. Acceptable for single-instance deployment.
 * - For multi-instance deployment, migrate to Redis SET.
 * - Scheduled cleanup removes expired tokens every hour to prevent unbounded growth.
 *
 * @author James Chase
 * @version 1.0 (P5: Token revocation for logout)
 * @since 2026-02-14
 */
@Service
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);

    private final Set<String> blacklist = ConcurrentHashMap.newKeySet();
    private final JwtUtil jwtUtil;

    public TokenBlacklistService(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    /**
     * Adds a token to the blacklist, preventing future authentication.
     *
     * @param token The JWT string to blacklist.
     */
    public void blacklist(String token) {
        blacklist.add(token);
    }

    /**
     * Checks if a token has been blacklisted (logged out).
     *
     * @param token The JWT string to check.
     * @return true if the token is revoked.
     */
    public boolean isBlacklisted(String token) {
        return blacklist.contains(token);
    }

    /**
     * Scheduled cleanup: Removes expired tokens from the blacklist.
     * Expired tokens are naturally invalid anyway, so keeping them wastes memory.
     * Runs every hour.
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    public void cleanupExpiredTokens() {
        int beforeSize = blacklist.size();
        Date now = new Date();

        blacklist.removeIf(token -> {
            try {
                Date expiration = jwtUtil.getExpiration(token);
                // Remove if expired or unparseable
                return expiration == null || expiration.before(now);
            } catch (Exception e) {
                // Unparseable token — safe to remove
                return true;
            }
        });

        int removed = beforeSize - blacklist.size();
        if (removed > 0) {
            log.info("Token blacklist cleanup: removed {} expired tokens, {} remaining",
                    removed, blacklist.size());
        }
    }
}
