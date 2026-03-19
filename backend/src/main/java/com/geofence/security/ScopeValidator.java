package com.geofence.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Validates that an API key's scopes permit the requested HTTP method.
 *
 * Scope model:
 *   "read"  — GET/HEAD requests only
 *   "write" — POST/PUT/PATCH/DELETE (also grants read)
 *   empty   — unrestricted (full-access key)
 *
 * Why method-based rather than path-based?  Path-based scope matrices are
 * brittle — every new endpoint requires a policy update.  Method-based scopes
 * map directly to the HTTP semantics (safe vs. unsafe), are self-documenting,
 * and match how Stripe/GitHub model read/write API key permissions.
 */
@Component
public class ScopeValidator {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    /**
     * Returns true if the key's scopes permit the given request.
     *
     * @param scopes  scopes array from the ApiKey entity (may be null or empty)
     * @param request the incoming HTTP request
     */
    public boolean isAllowed(String[] scopes, HttpServletRequest request) {
        if (scopes == null || scopes.length == 0) {
            return true;  // unrestricted key — full access
        }

        Set<String> scopeSet = Set.of(scopes);

        if (scopeSet.contains("write")) {
            return true;  // write implies read
        }

        String method = request.getMethod().toUpperCase();
        if (WRITE_METHODS.contains(method)) {
            return false;  // write method requires write scope
        }

        return scopeSet.contains("read");
    }
}
