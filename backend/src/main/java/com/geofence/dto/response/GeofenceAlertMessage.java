package com.geofence.dto.response;

import java.time.Instant;
import java.util.UUID;

public record GeofenceAlertMessage(
        String type,         // "ENTER" or "EXIT"
        UUID deviceId,
        String deviceName,
        UUID geofenceId,
        String geofenceName,
        Instant timestamp
) {}
