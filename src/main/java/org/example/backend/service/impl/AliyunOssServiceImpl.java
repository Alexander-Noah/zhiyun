package org.example.backend.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.OSSObject;
import org.example.backend.config.AliyunOssProperties;
import org.example.backend.service.AliyunOssService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AliyunOssServiceImpl implements AliyunOssService {
    private static final long MAX_AVATAR_SIZE = 2 * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Map<String, Set<String>> ALLOWED_EXTENSIONS_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", Set.of(".jpg", ".jpeg"),
            "image/png", Set.of(".png"),
            "image/webp", Set.of(".webp"),
            "image/gif", Set.of(".gif")
    );
    private static final Map<String, String> DEFAULT_EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif"
    );

    private final AliyunOssProperties properties;

    public AliyunOssServiceImpl(AliyunOssProperties properties) {
        this.properties = properties;
    }

    @Override
    public Map<String, String> uploadAvatar(MultipartFile file) {
        validateConfig();
        String contentType = validateFile(file);

        String objectKey = buildAvatarObjectKey(file.getOriginalFilename(), contentType);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(contentType);

        OSS ossClient = new OSSClientBuilder().build(
                properties.getEndpoint(),
                properties.getAccessKeyId(),
                properties.getAccessKeySecret()
        );

        try {
            ossClient.putObject(properties.getBucket(), objectKey, file.getInputStream(), metadata);
        } catch (IOException e) {
            throw new IllegalArgumentException("头像文件读取失败");
        } finally {
            ossClient.shutdown();
        }

        String publicUrl = buildPublicUrl(objectKey);
        return Map.of(
                "url", publicUrl,
                "avatarUrl", publicUrl,
                "objectKey", objectKey
        );
    }

    @Override
    public AvatarObject downloadAvatar(String objectKey) {
        validateConfig();
        String normalizedObjectKey = normalizeAvatarObjectKey(objectKey);
        OSS ossClient = new OSSClientBuilder().build(
                properties.getEndpoint(),
                properties.getAccessKeyId(),
                properties.getAccessKeySecret()
        );

        try {
            OSSObject ossObject = ossClient.getObject(properties.getBucket(), normalizedObjectKey);
            String contentType = ossObject.getObjectMetadata() == null ? "" : ossObject.getObjectMetadata().getContentType();
            try (InputStream objectContent = ossObject.getObjectContent()) {
                return new AvatarObject(objectContent.readAllBytes(), isBlank(contentType) ? "application/octet-stream" : contentType);
            }
        } catch (OSSException e) {
            throw new IllegalArgumentException("头像文件不存在或无法访问");
        } catch (IOException e) {
            throw new IllegalArgumentException("头像文件读取失败");
        } finally {
            ossClient.shutdown();
        }
    }

    private void validateConfig() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("阿里云 OSS 未启用，请配置 SMART_LAB_OSS_ENABLED=true");
        }
        if (isBlank(properties.getEndpoint()) || isBlank(properties.getBucket()) || isBlank(properties.getAccessKeyId()) || isBlank(properties.getAccessKeySecret())) {
            throw new IllegalStateException("阿里云 OSS 配置不完整，请检查 endpoint、bucket、accessKeyId、accessKeySecret");
        }
    }

    private String validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择头像文件");
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new IllegalArgumentException("头像文件不能超过 2MB");
        }

        String contentType = normalizeContentType(file.getContentType());
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("头像仅支持 JPG、PNG、WEBP、GIF 格式");
        }
        validateExtension(file.getOriginalFilename(), contentType);
        validateImageSignature(file, contentType);
        return contentType;
    }

    private void validateExtension(String originalFilename, String contentType) {
        String ext = getRawFileExtension(originalFilename);
        if (!ext.isEmpty() && !ALLOWED_EXTENSIONS_BY_CONTENT_TYPE.getOrDefault(contentType, Set.of()).contains(ext)) {
            throw new IllegalArgumentException("头像文件扩展名与内容类型不一致");
        }
    }

    private void validateImageSignature(MultipartFile file, String contentType) {
        byte[] signature = readSignature(file);
        boolean matches = switch (contentType) {
            case "image/jpeg" -> hasBytes(signature, 0xFF, 0xD8, 0xFF);
            case "image/png" -> hasBytes(signature, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/gif" -> hasAscii(signature, "GIF87a") || hasAscii(signature, "GIF89a");
            case "image/webp" -> hasAscii(signature, 0, "RIFF") && hasAscii(signature, 8, "WEBP");
            default -> false;
        };
        if (!matches) {
            throw new IllegalArgumentException("头像文件内容与图片格式不一致");
        }
    }

    private byte[] readSignature(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] signature = new byte[12];
            int read = inputStream.readNBytes(signature, 0, signature.length);
            return read == signature.length ? signature : Arrays.copyOf(signature, read);
        } catch (IOException e) {
            throw new IllegalArgumentException("头像文件读取失败");
        }
    }

    private boolean hasBytes(byte[] signature, int... bytes) {
        if (signature.length < bytes.length) {
            return false;
        }
        for (int i = 0; i < bytes.length; i++) {
            if ((signature[i] & 0xFF) != bytes[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean hasAscii(byte[] signature, String text) {
        return hasAscii(signature, 0, text);
    }

    private boolean hasAscii(byte[] signature, int offset, String text) {
        if (signature.length < offset + text.length()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (signature[offset + i] != (byte) text.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private String buildAvatarObjectKey(String originalFilename, String contentType) {
        String ext = getFileExtension(originalFilename, contentType);
        String datePath = LocalDate.now().toString().replace("-", "/");
        String avatarDir = trimSlashes(properties.getAvatarDir());
        return avatarDir + "/" + datePath + "/" + UUID.randomUUID() + ext;
    }

    private String normalizeAvatarObjectKey(String objectKey) {
        if (isBlank(objectKey)) {
            throw new IllegalArgumentException("头像文件地址无效");
        }
        String normalizedObjectKey = trimSlashes(objectKey);
        if (normalizedObjectKey.contains("..")) {
            throw new IllegalArgumentException("头像文件地址无效");
        }
        String avatarDir = trimSlashes(properties.getAvatarDir());
        if (!normalizedObjectKey.startsWith(avatarDir + "/")) {
            throw new IllegalArgumentException("头像文件地址无效");
        }
        return normalizedObjectKey;
    }

    private String getFileExtension(String filename, String contentType) {
        String ext = getRawFileExtension(filename);
        if (!ext.isEmpty()) {
            return ext;
        }
        return DEFAULT_EXTENSION_BY_CONTENT_TYPE.getOrDefault(contentType, ".png");
    }

    private String getRawFileExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex).toLowerCase();
    }

    private String normalizeContentType(String contentType) {
        return contentType == null ? "" : contentType.trim().toLowerCase();
    }

    private String buildPublicUrl(String objectKey) {
        if (!isBlank(properties.getPublicBaseUrl())) {
            return trimRightSlash(properties.getPublicBaseUrl()) + "/" + objectKey;
        }
        String endpoint = properties.getEndpoint().replaceFirst("^https?://", "");
        return "https://" + properties.getBucket() + "." + endpoint + "/" + objectKey;
    }

    private String trimSlashes(String value) {
        String text = isBlank(value) ? "avatars" : value.trim();
        return text.replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private String trimRightSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
