package org.example.backend.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalDevelopmentSecretsInitializerTest {
    @Test
    void createsRequiredSecretsWhenRunningFromDevelopmentDirectory() throws Exception {
        Path projectDir = newTestDirectory().resolve("Backend");
        Files.createDirectories(projectDir.resolve("src/main/java/org/example/backend"));
        Files.writeString(
                projectDir.resolve("src/main/java/org/example/backend/BackendApplication.java"),
                "package org.example.backend;"
        );
        Files.writeString(projectDir.resolve("pom.xml"), "<project />");

        ensureLocalDevelopmentSecrets(projectDir);

        Path secretsPath = projectDir.resolve(".local-dev-secrets.properties");
        Properties properties = new Properties();
        try (var inputStream = Files.newInputStream(secretsPath)) {
            properties.load(inputStream);
        }

        assertAll(
                () -> assertTrue(properties.getProperty("SMART_LAB_JWT_SECRET", "").length() >= 32),
                () -> assertTrue(properties.getProperty("ACADEMIC_CREDENTIAL_SECRET", "").length() >= 32)
        );
    }

    @Test
    void doesNotCreateSecretsOutsideDevelopmentDirectory() throws Exception {
        Path tempDir = newTestDirectory();

        ensureLocalDevelopmentSecrets(tempDir);

        assertFalse(Files.exists(tempDir.resolve(".local-dev-secrets.properties")));
    }

    private Path newTestDirectory() throws Exception {
        Path directory = Path.of("target", "local-dev-secrets-test", String.valueOf(System.nanoTime()));
        Files.createDirectories(directory);
        return directory;
    }

    private void ensureLocalDevelopmentSecrets(Path workingDirectory) throws Exception {
        Method ensure = Class.forName("org.example.backend.config.LocalDevelopmentSecretsInitializer")
                .getMethod("ensure", Path.class);
        ensure.invoke(null, workingDirectory);
    }
}
