package com.geofence.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Stateless principal reconstructed from JWT claims — no DB query per request.
 * Trade-off: a deleted user's token remains valid until expiry (24h by default).
 * Acceptable for an internship project; production would use short-lived tokens + refresh.
 */
public record JwtUserDetails(UUID userId, String email) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override public String getUsername()               { return email; }
    @Override public String getPassword()               { return null; }
    @Override public boolean isAccountNonExpired()      { return true; }
    @Override public boolean isAccountNonLocked()       { return true; }
    @Override public boolean isCredentialsNonExpired()  { return true; }
    @Override public boolean isEnabled()                { return true; }
}
