package com.geofence.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Email(message = "must be a valid email address")
        @NotBlank(message = "email is required")
        String email,

        @NotBlank(message = "password is required")
        String password
) {}
