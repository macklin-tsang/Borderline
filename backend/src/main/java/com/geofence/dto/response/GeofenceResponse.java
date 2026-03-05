package com.geofence.dto.response;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record GeofenceResponse(
        UUID id,
        String name,
        Map<String, Object> geometry,   // GeoJSON geometry object
        boolean active,
        boolean alertOnEntry,
        boolean alertOnExit,
        Instant createdAt
) {}
