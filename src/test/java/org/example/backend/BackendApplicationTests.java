package org.example.backend;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendApplicationTests {
    @Test
    void applicationEntryPointIsLoadable() {
        assertDoesNotThrow(() -> Class.forName("org.example.backend.BackendApplication"));
    }

    @Test
    void applicationEntryPointPreparesLocalDevelopmentSecretsBeforeSpringStarts() throws Exception {
        String source = Files.readString(Path.of("src/main/java/org/example/backend/BackendApplication.java"));

        int secretsIndex = source.indexOf("LocalDevelopmentSecretsInitializer.ensure");
        int springRunIndex = source.indexOf("SpringApplication.run");

        assertTrue(secretsIndex >= 0);
        assertTrue(springRunIndex > secretsIndex);
    }
}
