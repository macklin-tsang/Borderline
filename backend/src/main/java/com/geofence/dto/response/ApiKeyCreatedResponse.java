package com.geofence.dto.response;

import java.time.Instant;
import java.util.UUID;

// Returned only on creation — rawKey shown exactly once, never stored
public record ApiKeyCreatedResponse(
        UUID id,
        String name,
        String rawKey,          // full key — show to user once, then discard
        String keyPrefix,
        String[] scopes,
        int rateLimitPerMinute,
        Instant expiresAt,
        Instant createdAt
) {}
