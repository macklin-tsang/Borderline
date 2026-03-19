package com.geofence.dto.response;

import java.time.Instant;
import java.util.UUID;

public record RequestLogResponse(
        Long id,
        UUID apiKeyId,
        String endpoint,
        String method,
        int statusCode,
        Integer durationMs,
        boolean wasBlocked,
        String blockReason,
        Instant createdAt
) {}
