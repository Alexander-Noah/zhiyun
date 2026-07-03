package org.example.backend.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AliyunOssServiceImplSecurityTest {
    @Test
    void rejectsDisguisedImageUpload() throws Exception {
        Object service = newAliyunOssService();
        MultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "not-a-real-image".getBytes(StandardCharsets.UTF_8)
        );

        assertThrows(IllegalArgumentException.class, () -> invokeValidateFile(service, file));
    }

    @Test
    void rejectsContentTypeAndExtensionMismatch() throws Exception {
        Object service = newAliyunOssService();
        MultipartFile file = new MockMultipartFile(
                "file",
                "avatar.gif",
                "image/png",
                validPngBytes()
        );

        assertThrows(IllegalArgumentException.class, () -> invokeValidateFile(service, file));
    }

    @Test
    void acceptsValidPngSignature() throws Exception {
        Object service = newAliyunOssService();
        MultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                validPngBytes()
        );

        assertDoesNotThrow(() -> invokeValidateFile(service, file));
    }

    private Object newAliyunOssService() throws Exception {
        Class<?> propertiesClass = Class.forName("org.example.backend.config.AliyunOssProperties");
        Object properties = propertiesClass.getConstructor().newInstance();
        return Class.forName("org.example.backend.service.impl.AliyunOssServiceImpl")
                .getConstructor(propertiesClass)
                .newInstance(properties);
    }

    private Object invokeValidateFile(Object service, MultipartFile file) throws Exception {
        try {
            Method method = service.getClass().getDeclaredMethod("validateFile", MultipartFile.class);
            method.setAccessible(true);
            return method.invoke(service, file);
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

    private byte[] validPngBytes() {
        return new byte[]{
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 'I', 'H', 'D', 'R'
        };
    }
}
