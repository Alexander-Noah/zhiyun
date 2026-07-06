package org.example.backend.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;

public final class LocalDevelopmentSecretsInitializer {
    private static final String SECRETS_FILE = ".local-dev-secrets.properties";
    private static final String JWT_SECRET = "SMART_LAB_JWT_SECRET";
    private static final String ACADEMIC_SECRET = "ACADEMIC_CREDENTIAL_SECRET";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private LocalDevelopmentSecretsInitializer() {
    }

    public static void ensure(Path workingDirectory) {
        Path baseDirectory = workingDirectory == null
                ? Path.of("").toAbsolutePath().normalize()
                : workingDirectory.toAbsolutePath().normalize();
        if (!isDevelopmentDirectory(baseDirectory)) {
            return;
        }

        Path secretsPath = baseDirectory.resolve(SECRETS_FILE);
        Properties secrets = readSecrets(secretsPath);
        boolean changed = false;

        if (isBlank(secrets.getProperty(JWT_SECRET))) {
            secrets.setProperty(JWT_SECRET, newSecret());
            changed = true;
        }
        if (isBlank(secrets.getProperty(ACADEMIC_SECRET))) {
            secrets.setProperty(ACADEMIC_SECRET, newSecret());
            changed = true;
        }

        if (changed) {
            writeSecrets(secretsPath, secrets);
        }
    }

    private static boolean isDevelopmentDirectory(Path directory) {
        return Files.isRegularFile(directory.resolve("pom.xml"))
                && Files.isDirectory(directory.resolve("src/main"))
                && Files.isRegularFile(directory.resolve("src/main/java/org/example/backend/BackendApplication.java"));
    }

    private static Properties readSecrets(Path secretsPath) {
        Properties secrets = new Properties();
        if (!Files.exists(secretsPath)) {
            return secrets;
        }

        try (InputStream inputStream = Files.newInputStream(secretsPath)) {
            secrets.load(inputStream);
            return secrets;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read local development secrets: " + secretsPath, exception);
        }
    }

    private static void writeSecrets(Path secretsPath, Properties secrets) {
        try (OutputStream outputStream = Files.newOutputStream(secretsPath)) {
            secrets.store(outputStream, "Generated local development secrets. Do not commit this file.");
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write local development secrets: " + secretsPath, exception);
        }
    }

    private static String newSecret() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
