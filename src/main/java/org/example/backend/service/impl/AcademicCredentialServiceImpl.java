package org.example.backend.service.impl;

import org.example.backend.entity.AcademicCredentialView;
import org.example.backend.entity.AcademicScheduleImportRequest;
import org.example.backend.service.AcademicCredentialService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

@Service
public class AcademicCredentialServiceImpl implements AcademicCredentialService {
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int MIN_SECRET_LENGTH = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Path storePath;
    private final SecretKeySpec secretKey;

    public AcademicCredentialServiceImpl(
            @Value("${academic.credentials.store-path:data/academic-credentials.properties}") String storePath,
            @Value("${academic.credentials.secret:${ACADEMIC_CREDENTIAL_SECRET:}}") String secret
    ) {
        this.storePath = Path.of(storePath);
        this.secretKey = buildSecretKey(requireStrongSecret(secret, "ACADEMIC_CREDENTIAL_SECRET"));
    }

    private String requireStrongSecret(String value, String name) {
        String secret = value == null ? "" : value.trim();
        if (secret.isBlank()) {
            throw new IllegalStateException(name + " must be configured");
        }
        if (secret.toLowerCase().contains("change-me") || secret.toLowerCase().contains("dev-secret")) {
            throw new IllegalStateException(name + " must not use the development default");
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(name + " must be at least 32 characters");
        }
        return secret;
    }

    @Override
    public synchronized AcademicCredentialView saveCredential(AcademicScheduleImportRequest request) {
        String credentialKey = resolveCredentialKey(request);
        if (isBlank(request.getUsername()) || isBlank(request.getPassword())) {
            throw new IllegalArgumentException("保存教务凭据需要账号和密码");
        }

        Map<String, StoredCredential> credentials = readCredentials();
        StoredCredential credential = new StoredCredential();
        credential.setUsername(encrypt(request.getUsername()));
        credential.setPassword(encrypt(request.getPassword()));
        credential.setUpdatedAt(LocalDateTime.now().toString());
        credentials.put(credentialKey, credential);
        writeCredentials(credentials);
        return toView(credentialKey, credential);
    }

    @Override
    public synchronized AcademicCredentialView getCredentialView(String credentialKey) {
        String resolvedKey = normalizeCredentialKey(credentialKey);
        StoredCredential credential = readCredentials().get(resolvedKey);
        return toView(resolvedKey, credential);
    }

    @Override
    public synchronized void deleteCredential(String credentialKey) {
        String resolvedKey = normalizeCredentialKey(credentialKey);
        Map<String, StoredCredential> credentials = readCredentials();
        credentials.remove(resolvedKey);
        writeCredentials(credentials);
    }

    @Override
    public synchronized void applySavedCredential(AcademicScheduleImportRequest request) {
        String credentialKey = resolveCredentialKey(request);
        StoredCredential credential = readCredentials().get(credentialKey);
        if (credential == null) {
            throw new IllegalArgumentException("未配置本地教务账号，请先保存账号密码");
        }
        request.setUsername(decrypt(credential.getUsername()));
        request.setPassword(decrypt(credential.getPassword()));
    }

    private Map<String, StoredCredential> readCredentials() {
        if (!Files.exists(storePath)) {
            return new LinkedHashMap<>();
        }
        Properties properties = new Properties();
        try {
            try (InputStream inputStream = Files.newInputStream(storePath)) {
                properties.load(inputStream);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("读取本地教务凭据失败", exception);
        }

        Map<String, StoredCredential> credentials = new LinkedHashMap<>();
        for (String name : properties.stringPropertyNames()) {
            int dotIndex = name.lastIndexOf('.');
            if (dotIndex <= 0) {
                continue;
            }
            String credentialKey = name.substring(0, dotIndex);
            String field = name.substring(dotIndex + 1);
            StoredCredential credential = credentials.computeIfAbsent(credentialKey, key -> new StoredCredential());
            switch (field) {
                case "username" -> credential.setUsername(properties.getProperty(name));
                case "password" -> credential.setPassword(properties.getProperty(name));
                case "updatedAt" -> credential.setUpdatedAt(properties.getProperty(name));
                default -> {
                }
            }
        }
        return credentials;
    }

    private void writeCredentials(Map<String, StoredCredential> credentials) {
        try {
            Path parent = storePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Properties properties = new Properties();
            credentials.forEach((credentialKey, credential) -> {
                properties.setProperty(credentialKey + ".username", firstNonBlank(credential.getUsername(), ""));
                properties.setProperty(credentialKey + ".password", firstNonBlank(credential.getPassword(), ""));
                properties.setProperty(credentialKey + ".updatedAt", firstNonBlank(credential.getUpdatedAt(), ""));
            });
            try (OutputStream outputStream = Files.newOutputStream(storePath)) {
                properties.store(outputStream, "Encrypted academic system credentials");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("保存本地教务凭据失败", exception);
        }
    }

    private AcademicCredentialView toView(String credentialKey, StoredCredential credential) {
        AcademicCredentialView view = new AcademicCredentialView();
        view.setCredentialKey(credentialKey);
        view.setConfigured(credential != null);
        if (credential != null) {
            view.setUsernameMasked(maskUsername(decrypt(credential.getUsername())));
            view.setUpdatedAt(credential.getUpdatedAt());
        }
        return view;
    }

    private String encrypt(String value) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception exception) {
            throw new IllegalStateException("教务凭据加密失败", exception);
        }
    }

    private String decrypt(String value) {
        try {
            byte[] payload = Base64.getDecoder().decode(value);
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[payload.length - IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            System.arraycopy(payload, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("教务凭据解密失败，请确认 academic.credentials.secret 未变更", exception);
        }
    }

    private SecretKeySpec buildSecretKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] key = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(key, "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("初始化教务凭据密钥失败", exception);
        }
    }

    private String resolveCredentialKey(AcademicScheduleImportRequest request) {
        return normalizeCredentialKey(request == null ? null : request.getCredentialKey());
    }

    private String normalizeCredentialKey(String credentialKey) {
        if (isBlank(credentialKey)) {
            return "default";
        }
        return credentialKey.replaceAll("[^a-zA-Z0-9_.-]", "_");
    }

    private String maskUsername(String username) {
        if (isBlank(username)) {
            return "";
        }
        if (username.length() <= 4) {
            return username.charAt(0) + "***";
        }
        return username.substring(0, 2) + "****" + username.substring(username.length() - 2);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    public static class StoredCredential {
        private String username;
        private String password;
        private String updatedAt;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(String updatedAt) {
            this.updatedAt = updatedAt;
        }
    }
}
