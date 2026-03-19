package com.geofence.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Thread-safe HMAC-SHA256 service.
 *
 * Why HMAC instead of BCrypt for API keys?
 * BCrypt is non-deterministic (random salt per hash), so given an incoming raw key
 * you cannot look up its matching row without scanning every row and calling matches().
 * HMAC-SHA256 with a server secret is deterministic: the same input always produces
 * the same hash, enabling an indexed prefix lookup + O(1) hash comparison.
 * Trade-off: compromise of the server secret enables offline brute-force of key hashes.
 * Mitigation: rotate via hmac_key_version field; store secret in environment, not code.
 */
@Service
public class HmacService {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec keySpec;
    private final int keyVersion;

    public HmacService(@Value("${app.hmac.secret}") String secret,
                       @Value("${app.hmac.key-version}") int keyVersion) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "app.hmac.secret must be at least 32 characters (256 bits)");
        }
        this.keySpec = new SecretKeySpec(secretBytes, ALGORITHM);
        this.keyVersion = keyVersion;
    }

    /**
     * Computes HMAC-SHA256 of {@code input} using the configured server secret.
     * Creates a new Mac instance per call — Mac is not thread-safe.
     */
    public String compute(String input) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(keySpec);
            byte[] hash = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }

    public int getKeyVersion() {
        return keyVersion;
    }
}
