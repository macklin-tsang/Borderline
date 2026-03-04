package com.geofence;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:tc:postgis:16-3.4:///borderline",
    "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
    "spring.flyway.enabled=false",
    "app.jwt.secret=test-jwt-secret-min-32-chars-long-enough",
    "app.hmac.secret=test-hmac-secret",
    "app.geoip.url=http://localhost:9999"
})
class BorderlineApplicationTest {

    @Test
    void contextLoads() {
    }
}
