package com.geofence.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Redis-backed fixed-window rate limiter using a Lua script for atomicity.
 *
 * Why Lua? A naive INCR + EXPIRE pair is NOT atomic: if the process crashes between
 * the two commands, the key never expires and the bucket stays blocked forever.
 * The Lua script runs atomically on the Redis server — either both commands execute
 * or neither does. This is the correct pattern for Redis rate limiting.
 *
 * Two independent buckets per request:
 *   1. Per-API-key:  limit comes from api_keys.rate_limit_per_minute (DB-configured per key)
 *   2. Per-source-IP: fixed ceiling (DEFAULT_IP_LIMIT) to contain abusive clients
 * Both must pass; rejection on either returns 429.
 */
@Service
public class RateLimitService {

    private static final int WINDOW_SECONDS = 60;
    private static final int DEFAULT_IP_LIMIT = 600;   // 10 req/s sustained per IP

    // Atomically: INCR the counter; if it was just created (first request in this window),
    // set its expiry to WINDOW_SECONDS so it auto-clears after the window resets.
    private static final RedisScript<Long> INCREMENT_SCRIPT = RedisScript.of("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
            end
            return current
            """, Long.class);

    private final StringRedisTemplate redis;

    public RateLimitService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Returns true if the request is within limits, false if it should be rejected (429).
     *
     * @param apiKeyId         UUID string of the API key (for per-key bucket key)
     * @param limitPerMinute   per-key limit from the database row
     * @param clientIp         source IP (for secondary per-IP bucket)
     */
    public boolean isAllowed(String apiKeyId, int limitPerMinute, String clientIp) {
        String windowArg = String.valueOf(WINDOW_SECONDS);

        // Per-key bucket — fail closed: null means Redis unavailable → deny
        Long keyCount = redis.execute(INCREMENT_SCRIPT,
                List.of("rl:key:" + apiKeyId), windowArg);
        if (keyCount == null || keyCount > limitPerMinute) {
            return false;
        }

        // Per-IP secondary bucket — catches key-sharing abuse; fail closed on null
        Long ipCount = redis.execute(INCREMENT_SCRIPT,
                List.of("rl:ip:" + clientIp), windowArg);
        return ipCount != null && ipCount <= DEFAULT_IP_LIMIT;
    }
}
