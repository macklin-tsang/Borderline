package com.geofence.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Email(message = "must be a valid email address")
        @NotBlank(message = "email is required")
        String email,

        @NotBlank(message = "password is required")
        @Size(max = 100, message = "password must not exceed 100 characters")
        String password
) {}
