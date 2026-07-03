package org.example.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UserProfileEndpointTest {
    @Test
    void exposesSelfServiceProfileUpdateEndpoint() {
        boolean hasProfileUpdateEndpoint = Arrays.stream(controllerClass().getDeclaredMethods())
                .map(method -> method.getAnnotation(PutMapping.class))
                .filter(annotation -> annotation != null)
                .flatMap(annotation -> Arrays.stream(annotation.value()))
                .anyMatch("/auth/profile"::equals);

        assertTrue(hasProfileUpdateEndpoint, "current user profile edits must use PUT /auth/profile");
    }

    private Class<?> controllerClass() {
        try {
            return Class.forName("org.example.backend.controller.UserController");
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }
}
