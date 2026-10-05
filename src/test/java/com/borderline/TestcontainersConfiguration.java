package com.borderline;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        // Same image as docker-compose.yml: our schema needs the PostGIS extension
        DockerImageName postgis = DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres");
        return new PostgreSQLContainer(postgis);
    }

}
