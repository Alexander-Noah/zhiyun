package org.example.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class PasswordService {
    private static final String PREFIX = "pbkdf2";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;

    private final SecureRandom secureRandom = new SecureRandom();
    private final int iterations;

    public PasswordService(@Value("${smart-lab.security.password.iterations:120000}") int iterations) {
        this.iterations = Math.max(iterations, 10000);
    }

    public String encode(String rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        byte[] hash = pbkdf2(rawPassword, salt, iterations);

        return String.join("$",
                PREFIX,
                String.valueOf(iterations),
                Base64.getEncoder().encodeToString(salt),
                Base64.getEncoder().encodeToString(hash)
        );
    }

    public boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null || storedPassword.isBlank()) {
            return false;
        }

        if (!isEncoded(storedPassword)) {
            return MessageDigest.isEqual(
                    rawPassword.getBytes(StandardCharsets.UTF_8),
                    storedPassword.getBytes(StandardCharsets.UTF_8)
            );
        }

        try {
            String[] parts = storedPassword.split("\\$");
            int storedIterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[3]);
            byte[] actualHash = pbkdf2(rawPassword, salt, storedIterations);

            return MessageDigest.isEqual(expectedHash, actualHash);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public boolean needsRehash(String storedPassword) {
        if (!isEncoded(storedPassword)) {
            return true;
        }

        try {
            String[] parts = storedPassword.split("\\$");
            return Integer.parseInt(parts[1]) < iterations;
        } catch (RuntimeException exception) {
            return true;
        }
    }

    public boolean isEncoded(String password) {
        return password != null && password.startsWith(PREFIX + "$") && password.split("\\$").length == 4;
    }

    private byte[] pbkdf2(String rawPassword, byte[] salt, int iterationCount) {
        try {
            PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterationCount, HASH_BITS);
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (Exception exception) {
            throw new IllegalStateException("password hash failed", exception);
        }
    }
}
