package org.example.backend.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisHealthConfigurationTest {
    @Test
    void redisHealthCheckIsOptInForLocalDevelopment() throws IOException {
        String applicationProperties = Files.readString(Path.of("src/main/resources/application.properties"));

        assertTrue(applicationProperties.contains(
                "management.health.redis.enabled=${SMART_LAB_REDIS_HEALTH_ENABLED:false}"
        ));
    }
}
