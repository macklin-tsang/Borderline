package com.geofence.service;

import com.geofence.dto.request.ApiKeyCreateRequest;
import com.geofence.dto.response.ApiKeyCreatedResponse;
import com.geofence.dto.response.ApiKeyResponse;
import com.geofence.exception.ApiKeyNotFoundException;
import com.geofence.model.ApiKey;
import com.geofence.repository.ApiKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ApiKeyService {

    // 24 random bytes → 48 hex chars; prefix = first 8, remainder is the secret body.
    // Format mirrors Stripe: short prefix for lookup + long random suffix for entropy.
    private static final int KEY_BYTES = 24;
    private static final int PREFIX_LENGTH = 8;

    private final ApiKeyRepository repo;
    private final HmacService hmacService;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyService(ApiKeyRepository repo, HmacService hmacService) {
        this.repo = repo;
        this.hmacService = hmacService;
    }

    /**
     * Generates a new API key for the given user.
     *
     * Crypto work (SecureRandom + HMAC) is intentionally done BEFORE the
     * @Transactional boundary. The class-level @Transactional was removed so that
     * a DB connection is not checked out of HikariCP until the save() call below —
     * holding a connection while doing CPU-bound crypto would waste pool capacity
     * under concurrent key-generation requests.
     */
    @Transactional
    public ApiKeyCreatedResponse generate(UUID userId, ApiKeyCreateRequest req) {
        // --- All CPU-bound crypto happens here, outside any DB transaction ---
        byte[] randomBytes = new byte[KEY_BYTES];
        secureRandom.nextBytes(randomBytes);
        String rawKey = HexFormat.of().formatHex(randomBytes);   // 48 hex chars

        String prefix = rawKey.substring(0, PREFIX_LENGTH);
        String hash = hmacService.compute(rawKey);

        String[] scopes = req.scopes() != null ? req.scopes() : new String[0];
        int rateLimit = req.rateLimitPerMinute();

        // --- Narrow transaction: only the DB write acquires a connection ---
        ApiKey apiKey = new ApiKey(userId, req.name(), prefix, hash,
                hmacService.getKeyVersion(), scopes, rateLimit, req.expiresAt());
        repo.save(apiKey);

        return new ApiKeyCreatedResponse(
                apiKey.getId(), apiKey.getName(), rawKey, prefix,
                scopes, rateLimit, apiKey.getExpiresAt(), apiKey.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> list(UUID userId) {
        return repo.findByUserIdAndActiveTrue(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void revoke(UUID userId, UUID id) {
        ApiKey apiKey = repo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiKeyNotFoundException(id));
        apiKey.setActive(false);
        repo.save(apiKey);
    }

    /**
     * Validates a raw API key from an incoming request.
     * Two-step: prefix lookup (B-tree index) → HMAC verification.
     * Returns the matching ApiKey if valid, active, and not expired.
     */
    @Transactional(readOnly = true)
    public Optional<ApiKey> validate(String rawKey) {
        if (rawKey == null || rawKey.length() < PREFIX_LENGTH) {
            return Optional.empty();
        }
        String prefix = rawKey.substring(0, PREFIX_LENGTH);
        String expectedHash = hmacService.compute(rawKey);
        byte[] expectedHashBytes = expectedHash.getBytes(StandardCharsets.UTF_8);
        return repo.findByKeyPrefixAndActiveTrue(prefix).stream()
                // Constant-time comparison prevents timing oracle attacks.
                // String.equals() short-circuits on first differing byte; MessageDigest.isEqual
                // always compares all bytes. This is the same pattern used by Stripe/GitHub.
                .filter(k -> MessageDigest.isEqual(
                        k.getKeyHash().getBytes(StandardCharsets.UTF_8), expectedHashBytes))
                .filter(k -> k.getExpiresAt() == null || k.getExpiresAt().isAfter(Instant.now()))
                .findFirst();
    }

    private ApiKeyResponse toResponse(ApiKey k) {
        return new ApiKeyResponse(
                k.getId(), k.getName(), k.getKeyPrefix(),
                k.getScopes(), k.getRateLimitPerMinute(),
                k.getExpiresAt(), k.isActive(), k.getCreatedAt());
    }
}
