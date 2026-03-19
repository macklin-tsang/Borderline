package com.geofence.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeviceRenameRequest(
        @NotBlank(message = "name is required")
        @Size(max = 255)
        String name
) {}
