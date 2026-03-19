package com.geofence.dto.response;

import java.time.Instant;
import java.util.UUID;

// Returned for list/get — raw key is NEVER re-exposed after creation
public record ApiKeyResponse(
        UUID id,
        String name,
        String keyPrefix,       // first 8 chars — safe to display (not the full key)
        String[] scopes,
        int rateLimitPerMinute,
        Instant expiresAt,
        boolean active,
        Instant createdAt
) {}
