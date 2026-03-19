package com.geofence.security;

import org.springframework.security.core.Authentication;

import java.util.UUID;

public final class AuthUtils {

    private AuthUtils() {}

    public static UUID userId(Authentication auth) {
        Object principal = auth.getPrincipal();
        if (principal instanceof JwtUserDetails j) return j.userId();
        if (principal instanceof ApiKeyPrincipal a) return a.userId();
        throw new IllegalStateException("Unknown principal: " + principal.getClass());
    }
}
