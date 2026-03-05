package com.geofence.security;

import com.geofence.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-chars-long";
    private static final long EXPIRY_MS = 3_600_000L;

    private JwtService jwtService;
    private User testUser;

    @BeforeEach
    void setUp() throws Exception {
        jwtService = new JwtService(SECRET, EXPIRY_MS);
        testUser = new User("user@example.com", "hashed");
        setId(testUser, UUID.randomUUID());
    }

    @Test
    void extractDetails_returnsCorrectUserIdAndEmail() {
        String token = jwtService.generate(testUser);
        JwtService.JwtDetails details = jwtService.extractDetails(token);

        assertThat(details.userId()).isEqualTo(testUser.getId());
        assertThat(details.email()).isEqualTo(testUser.getEmail());
    }

    @Test
    void isValid_returnsFalseForMalformedTokens() {
        assertThat(jwtService.isValid("not-a-jwt")).isFalse();
        assertThat(jwtService.isValid("")).isFalse();
        assertThat(jwtService.isValid("a.b.c")).isFalse();
    }

    @Test
    void isValid_returnsFalseForTokenFromDifferentSecret() {
        JwtService otherService = new JwtService("completely-different-secret-32chars!!", EXPIRY_MS);
        String tokenFromOther = otherService.generate(testUser);

        assertThat(jwtService.isValid(tokenFromOther)).isFalse();
    }

    @Test
    void constructor_throwsOnShortSecret() {
        assertThatThrownBy(() -> new JwtService("tooshort", EXPIRY_MS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 characters");
    }

    private static void setId(User user, UUID id) throws Exception {
        Field field = User.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(user, id);
    }
}
