package com.geofence.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_request_logs")
public class ApiRequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_key_id")
    private UUID apiKeyId;

    @Column(nullable = false, length = 255)
    private String endpoint;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(name = "status_code", nullable = false)
    private int statusCode;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "was_blocked", nullable = false)
    private boolean wasBlocked = false;

    @Column(name = "block_reason", length = 50)
    private String blockReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    protected ApiRequestLog() {}

    public ApiRequestLog(UUID apiKeyId, String endpoint, String method, int statusCode,
                         Integer durationMs, boolean wasBlocked, String blockReason) {
        this.apiKeyId = apiKeyId;
        this.endpoint = endpoint;
        this.method = method;
        this.statusCode = statusCode;
        this.durationMs = durationMs;
        this.wasBlocked = wasBlocked;
        this.blockReason = blockReason;
    }

    public Long getId() { return id; }
    public UUID getApiKeyId() { return apiKeyId; }
    public String getEndpoint() { return endpoint; }
    public String getMethod() { return method; }
    public int getStatusCode() { return statusCode; }
    public Integer getDurationMs() { return durationMs; }
    public boolean isWasBlocked() { return wasBlocked; }
    public String getBlockReason() { return blockReason; }
    public Instant getCreatedAt() { return createdAt; }
}
