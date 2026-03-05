package com.geofence.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeviceCreateRequest(
        @NotBlank @Size(max = 255) String name
) {}
