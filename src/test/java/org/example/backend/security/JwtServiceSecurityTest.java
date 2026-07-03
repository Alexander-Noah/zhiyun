package org.example.backend.security;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceSecurityTest {
    @Test
    void rejectsBlankOrDevelopmentJwtSecret() {
        assertThrows(IllegalStateException.class, () -> newJwtService("", 7200, "smart-lab"));
        assertThrows(IllegalStateException.class, () -> newJwtService(
                "smart-lab-dev-jwt-secret-change-in-" + "production-2026",
                7200,
                "smart-lab"
        ));
    }

    @Test
    void acceptsStrongExplicitJwtSecret() {
        assertDoesNotThrow(() -> newJwtService("test-only-strong-jwt-secret-with-40-plus-chars", 7200, "smart-lab"));
    }

    private Object newJwtService(String secret, long expirationSeconds, String issuer) throws Exception {
        try {
            return Class.forName("org.example.backend.security.JwtService")
                    .getConstructor(String.class, long.class, String.class)
                    .newInstance(secret, expirationSeconds, issuer);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }
}
