package org.example.backend.controller;

import org.example.backend.service.AliyunOssService.AvatarObject;
import org.example.backend.result.Result;
import org.example.backend.service.AliyunOssService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@CrossOrigin
@RestController
public class FileUploadController {
    private final AliyunOssService aliyunOssService;

    public FileUploadController(AliyunOssService aliyunOssService) {
        this.aliyunOssService = aliyunOssService;
    }

    @PostMapping("/files/avatars")
    public Result uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.success("上传头像成功", aliyunOssService.uploadAvatar(file));
    }

    @GetMapping("/files/avatar-proxy")
    public ResponseEntity<byte[]> getAvatar(@RequestParam("objectKey") String objectKey) {
        AvatarObject avatarObject = aliyunOssService.downloadAvatar(objectKey);
        return ResponseEntity.ok()
                .contentType(parseMediaType(avatarObject.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .body(avatarObject.content());
    }

    private MediaType parseMediaType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
