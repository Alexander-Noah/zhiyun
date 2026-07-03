package org.example.backend;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class BackendApplicationTests {
    @Test
    void applicationEntryPointIsLoadable() {
        assertDoesNotThrow(() -> Class.forName("org.example.backend.BackendApplication"));
    }
}
