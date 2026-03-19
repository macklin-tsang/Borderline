package com.geofence.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record ApiKeyCreateRequest(
        @NotBlank @Size(max = 255) String name,
        String[] scopes,
        @NotNull @Min(1) @Max(10000) Integer rateLimitPerMinute,
        Instant expiresAt          // nullable — null means never expires
) {}
