package org.example.backend.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import org.example.backend.config.AliyunOssProperties;
import org.example.backend.service.AliyunOssService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AliyunOssServiceImpl implements AliyunOssService {
    private static final long MAX_AVATAR_SIZE = 2 * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final AliyunOssProperties properties;

    public AliyunOssServiceImpl(AliyunOssProperties properties) {
        this.properties = properties;
    }

    @Override
    public Map<String, String> uploadAvatar(MultipartFile file) {
        validateConfig();
        validateFile(file);

        String objectKey = buildAvatarObjectKey(file.getOriginalFilename());
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType());

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

        return Map.of(
                "url", buildPublicUrl(objectKey),
                "objectKey", objectKey
        );
    }

    private void validateConfig() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("阿里云OSS未启用，请配置 SMART_LAB_OSS_ENABLED=true");
        }
        if (isBlank(properties.getEndpoint()) || isBlank(properties.getBucket()) || isBlank(properties.getAccessKeyId()) || isBlank(properties.getAccessKeySecret())) {
            throw new IllegalStateException("阿里云OSS配置不完整，请检查 endpoint、bucket、accessKeyId、accessKeySecret");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择头像文件");
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new IllegalArgumentException("头像文件不能超过2MB");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("头像仅支持 JPG、PNG、WEBP、GIF 格式");
        }
    }

    private String buildAvatarObjectKey(String originalFilename) {
        String ext = getFileExtension(originalFilename);
        String datePath = LocalDate.now().toString().replace("-", "/");
        String avatarDir = trimSlashes(properties.getAvatarDir());
        return avatarDir + "/" + datePath + "/" + UUID.randomUUID() + ext;
    }

    private String getFileExtension(String filename) {
        if (filename == null) {
            return ".png";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return ".png";
        }
        return filename.substring(dotIndex).toLowerCase();
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
