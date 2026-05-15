package org.example.backend.controller;

import org.example.backend.result.Result;
import org.example.backend.service.AliyunOssService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@CrossOrigin
@RestController
public class FileUploadController {
    private final AliyunOssService aliyunOssService;

    public FileUploadController(AliyunOssService aliyunOssService) {
        this.aliyunOssService = aliyunOssService;
    }

    @PostMapping("/files/avatars")
    public Result uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.success("upload avatar success", aliyunOssService.uploadAvatar(file));
    }
}
