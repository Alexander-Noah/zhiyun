package org.example.backend.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface AliyunOssService {
    Map<String, String> uploadAvatar(MultipartFile file);
}
