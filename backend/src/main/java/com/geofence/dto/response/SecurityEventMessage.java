package com.geofence.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Security event sent to the user's security dashboard via WebSocket.
 *
 * Delivered to /user/{userId}/queue/security-events in batches every 500ms.
 */
public record SecurityEventMessage(
        String type,          // RATE_LIMIT | INVALID_KEY
        UUID apiKeyId,        // null for invalid-key events (key failed validation)
        String endpoint,
        String detail,
        Instant timestamp
) {}
