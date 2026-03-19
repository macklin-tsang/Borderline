package com.geofence.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    // First 8 chars of the full key — stored plaintext for O(1) prefix lookup (B-tree index).
    // Why not BCrypt? BCrypt is salted — you can't derive the stored hash from the incoming key
    // without knowing the stored salt. Prefix lookup + HMAC-SHA256 gives us fast lookup
    // with deterministic verification (same design as Stripe/GitHub API keys).
    @Column(name = "key_prefix", nullable = false, length = 8)
    private String keyPrefix;

    // HMAC-SHA256(fullKey, serverSecret) — deterministic, verifiable, not reversible.
    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    @Column(name = "hmac_key_version", nullable = false)
    private int hmacKeyVersion;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]", nullable = false)
    private String[] scopes = new String[0];

    @Column(name = "rate_limit_per_minute", nullable = false)
    private int rateLimitPerMinute = 100;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    protected ApiKey() {}

    public ApiKey(UUID userId, String name, String keyPrefix, String keyHash,
                  int hmacKeyVersion, String[] scopes, int rateLimitPerMinute, Instant expiresAt) {
        this.userId = userId;
        this.name = name;
        this.keyPrefix = keyPrefix;
        this.keyHash = keyHash;
        this.hmacKeyVersion = hmacKeyVersion;
        this.scopes = scopes != null ? scopes : new String[0];
        this.rateLimitPerMinute = rateLimitPerMinute;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public String getKeyPrefix() { return keyPrefix; }
    public String getKeyHash() { return keyHash; }
    public int getHmacKeyVersion() { return hmacKeyVersion; }
    public String[] getScopes() { return scopes; }
    public int getRateLimitPerMinute() { return rateLimitPerMinute; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }

    public void setActive(boolean active) { this.active = active; }
}
