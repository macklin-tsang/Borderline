package com.geofence.dto.response;

import java.time.Instant;
import java.util.UUID;

public record DeviceResponse(
        UUID id,
        String name,
        Double lastLat,
        Double lastLon,
        Instant lastSeen,
        Instant createdAt,
        boolean insideAnyGeofence
) {}
