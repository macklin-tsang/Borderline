package com.geofence.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record GeofenceRequest(
        @NotBlank String name,
        @NotNull Map<String, Object> geometry,   // GeoJSON geometry object
        boolean alertOnEntry,
        boolean alertOnExit
) {}
