package com.weightsmart.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
 * RateLimiterService
 * Implements the Token Bucket Algorithm to prevent Denial of Service (DoS)
 * and Enumeration attacks.
 *
 * Architecture Role:
 * 1. Security Guard: Limits the frequency of actions from a single IP address.
 * 2. State Management: Maintains in-memory buckets for active clients.
 * 3. Endpoint Scoping: Separate rate limits for search (lenient) vs. auth (strict).
 *
 * Bucket Configuration:
 * - Search: 10 tokens, refills at 10/minute (type-ahead is chatty)
 * - Auth (login/register): 5 tokens, refills at 5/minute (brute-force protection)
 *
 * Key Concepts:
 * Token Bucket: A mechanism where tokens are added at a fixed rate.
 * A request consumes a token. If the bucket is empty, the request is denied.
 *
 * @author James Chase
 * @version 2.0 (P5: Endpoint-scoped rate limiting)
 * @since 2026-01-31
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    // Cleanup configuration: Remove buckets idle for 10 minutes
    private static final long STALE_BUCKET_THRESHOLD_MS = 10 * 60 * 1000; // 10 minutes

    // Separate bucket maps for different rate limits
    private final Map<String, Bucket> searchBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> authBuckets = new ConcurrentHashMap<>();

    /**
     * Attempts to consume a search token for the given client key (IP).
     * Rate limit: 10 requests per minute (lenient for type-ahead).
     *
     * @param key The unique identifier for the client (usually IP address).
     * @return true if token consumed (Allowed), false if bucket empty (Rate Limited).
     */
    public boolean tryConsume(String key) {
        Bucket bucket = searchBuckets.computeIfAbsent(key, k -> new Bucket(10, 10.0 / 60000.0));
        return bucket.tryConsume();
    }

    /**
     * Attempts to consume an auth token for the given client key (IP).
     * Rate limit: 5 requests per minute (strict for brute-force protection).
     *
     * @param key The unique identifier for the client (usually IP address).
     * @return true if token consumed (Allowed), false if bucket empty (Rate Limited).
     */
    public boolean tryConsumeAuth(String key) {
        Bucket bucket = authBuckets.computeIfAbsent(key, k -> new Bucket(5, 5.0 / 60000.0));
        return bucket.tryConsume();
    }

    /**
     * Scheduled cleanup job to remove stale buckets and prevent memory leaks.
     * Runs every 5 minutes, removing buckets that haven't been accessed in 10 minutes.
     * Cleans both search and auth bucket maps.
     *
     * P1: Memory Leak Prevention
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void cleanupStaleBuckets() {
        long cutoff = System.currentTimeMillis() - STALE_BUCKET_THRESHOLD_MS;
        int beforeSize = searchBuckets.size() + authBuckets.size();

        searchBuckets.entrySet().removeIf(entry -> entry.getValue().getLastRefillTimestamp() < cutoff);
        authBuckets.entrySet().removeIf(entry -> entry.getValue().getLastRefillTimestamp() < cutoff);

        int removed = beforeSize - searchBuckets.size() - authBuckets.size();
        if (removed > 0) {
            log.info("Rate limiter cleanup: removed {} stale buckets, {} remaining",
                    removed, searchBuckets.size() + authBuckets.size());
        }
    }

    /**
     * Inner Class representing a single Token Bucket.
     * Configurable capacity and refill rate for different endpoint scopes.
     */
    private static class Bucket {
        private final int capacity;
        private final double refillRatePerMs;

        private double tokens;
        private long lastRefillTimestamp;

        Bucket(int capacity, double refillRatePerMs) {
            this.capacity = capacity;
            this.refillRatePerMs = refillRatePerMs;
            this.tokens = capacity;
            this.lastRefillTimestamp = System.currentTimeMillis();
        }

        /**
         * synchronized ensures thread safety for concurrent requests from the same IP.
         */
        synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long delta = now - lastRefillTimestamp;
            double tokensToAdd = delta * refillRatePerMs;

            if (tokensToAdd > 0) {
                tokens = Math.min(capacity, tokens + tokensToAdd);
                lastRefillTimestamp = now;
            }
        }

        /**
         * Returns the last refill timestamp for staleness checking.
         */
        long getLastRefillTimestamp() {
            return lastRefillTimestamp;
        }
    }
}
