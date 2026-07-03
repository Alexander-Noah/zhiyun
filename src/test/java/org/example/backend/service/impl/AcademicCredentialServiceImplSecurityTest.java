package org.example.backend.service.impl;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AcademicCredentialServiceImplSecurityTest {
    @Test
    void rejectsBlankAcademicCredentialSecret() {
        assertThrows(IllegalStateException.class, () -> newAcademicCredentialService("data/test-academic-credentials.properties", ""));
    }

    @Test
    void acceptsStrongAcademicCredentialSecret() {
        assertDoesNotThrow(() -> newAcademicCredentialService(
                "data/test-academic-credentials.properties",
                "test-only-strong-academic-secret-with-40-plus-chars"
        ));
    }

    private Object newAcademicCredentialService(String storePath, String secret) throws Exception {
        try {
            return Class.forName("org.example.backend.service.impl.AcademicCredentialServiceImpl")
                    .getConstructor(String.class, String.class)
                    .newInstance(storePath, secret);
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
