package com.geofence.exception;

import java.util.UUID;

public class GeofenceNotFoundException extends RuntimeException {
    public GeofenceNotFoundException(UUID id) {
        super("Geofence not found: " + id);
    }
}
