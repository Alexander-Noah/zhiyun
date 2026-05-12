package org.example.backend.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.backend.entity.AcademicScheduleImportRequest;
import org.example.backend.result.Result;
import org.example.backend.service.AcademicCredentialService;
import org.example.backend.service.AcademicScheduleService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@Slf4j
public class AcademicScheduleController {
    private final AcademicScheduleService academicScheduleService;
    private final AcademicCredentialService academicCredentialService;

    public AcademicScheduleController(
            AcademicScheduleService academicScheduleService,
            AcademicCredentialService academicCredentialService
    ) {
        this.academicScheduleService = academicScheduleService;
        this.academicCredentialService = academicCredentialService;
    }

    @PostMapping("/academic-schedules/parse")
    public Result parseSchedule(@RequestBody AcademicScheduleImportRequest request) {
        log.info("解析教务课表 HTML");
        return Result.success(academicScheduleService.parseSchedule(request));
    }

    @PostMapping("/academic-schedules/fetch")
    public Result fetchSchedule(@RequestBody AcademicScheduleImportRequest request) {
        log.info("抓取教务系统课表");
        return Result.success(academicScheduleService.fetchSchedule(request));
    }

    @PostMapping("/academic-schedules/import")
    public Result importSchedule(@RequestBody AcademicScheduleImportRequest request) {
        log.info("导入教务系统课表");
        return Result.success(academicScheduleService.importSchedule(request));
    }

    @GetMapping("/academic-schedules/credentials/{credentialKey}")
    public Result getCredential(@PathVariable String credentialKey) {
        log.info("查询本地教务账号配置");
        return Result.success(academicCredentialService.getCredentialView(credentialKey));
    }

    @PostMapping("/academic-schedules/credentials")
    public Result saveCredential(@RequestBody AcademicScheduleImportRequest request) {
        log.info("保存本地教务账号配置");
        return Result.success(academicCredentialService.saveCredential(request));
    }

    @DeleteMapping("/academic-schedules/credentials/{credentialKey}")
    public Result deleteCredential(@PathVariable String credentialKey) {
        log.info("删除本地教务账号配置");
        academicCredentialService.deleteCredential(credentialKey);
        return Result.success(true);
    }
}
