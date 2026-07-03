package org.example.backend.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalDevelopmentLaunchScriptTest {
    @Test
    void localStartupScriptSuppliesRequiredSecretsWithoutCommittingThem() throws IOException {
        Path scriptPath = Path.of("start-local.ps1");
        String script = Files.readString(scriptPath);
        String gitignore = Files.readString(Path.of("..", ".gitignore"));
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yaml"));

        assertAll(
                () -> assertTrue(script.contains("SMART_LAB_JWT_SECRET")),
                () -> assertTrue(script.contains("ACADEMIC_CREDENTIAL_SECRET")),
                () -> assertTrue(script.contains("SMART_LAB_DB_PASSWORD")),
                () -> assertTrue(script.contains(".local-dev-secrets.properties")),
                () -> assertTrue(script.contains("New-LocalSecret")),
                () -> assertTrue(gitignore.contains("Backend/.local-dev-secrets.properties")),
                () -> assertTrue(applicationYaml.contains("optional:file:.local-dev-secrets.properties"))
        );
    }
}
