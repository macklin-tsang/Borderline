package com.geofence.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Stateless principal for API-key-authenticated requests.
 * Scopes from the API key become SCOPE_* granted authorities,
 * enabling @PreAuthorize("hasAuthority('SCOPE_read')") on protected endpoints.
 */
public record ApiKeyPrincipal(UUID userId, UUID apiKeyId, String[] scopes) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (scopes == null || scopes.length == 0) {
            return List.of();
        }
        return Arrays.stream(scopes)
                .map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
                .toList();
    }

    // Principal name = userId string — used by SimpMessagingTemplate for WebSocket routing
    @Override public String getUsername()               { return userId.toString(); }
    @Override public String getPassword()               { return null; }
    @Override public boolean isAccountNonExpired()      { return true; }
    @Override public boolean isAccountNonLocked()       { return true; }
    @Override public boolean isCredentialsNonExpired()  { return true; }
    @Override public boolean isEnabled()                { return true; }
}
