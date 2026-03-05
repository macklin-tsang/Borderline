package com.geofence.security;

import com.geofence.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    // Used as both the write key in generate() and the read key in extractDetails().
    // A constant prevents the two sites from drifting out of sync.
    private static final String EMAIL_CLAIM = "email";

    private final SecretKey key;
    private final long expiryMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiry-ms}") long expiryMs) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 characters (256 bits) for HS256");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expiryMs = expiryMs;
    }

    public String generate(User user) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(EMAIL_CLAIM, user.getEmail())
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiryMs))
                .signWith(key)
                .compact();
    }

    /**
     * Parse the token once and return both fields. Callers should use this
     * instead of calling extractUserId + extractEmail separately (which would
     * parse and verify the signature twice).
     *
     * @throws JwtException if the token is invalid, expired, or tampered with
     */
    public JwtDetails extractDetails(String token) {
        Claims claims = parseClaims(token);
        String email = claims.get(EMAIL_CLAIM, String.class);
        if (email == null) {
            throw new JwtException("Token is missing required email claim");
        }
        return new JwtDetails(UUID.fromString(claims.getSubject()), email);
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public record JwtDetails(UUID userId, String email) {}
}
