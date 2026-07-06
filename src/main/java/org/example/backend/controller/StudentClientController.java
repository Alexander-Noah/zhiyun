package org.example.backend.controller;

import org.example.backend.entity.StudentClientEntity;
import org.example.backend.result.Result;
import org.example.backend.service.StudentClientService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
public class StudentClientController {
    private final StudentClientService studentClientService;

    public StudentClientController(StudentClientService studentClientService) {
        this.studentClientService = studentClientService;
    }

    @PostMapping({"/student-clients/online", "/api/student-clients/online"})
    public Result online(@RequestBody StudentClientEntity request) {
        return Result.success("student client online status registered", studentClientService.online(request));
    }

    @PostMapping({"/student-clients/offline", "/api/student-clients/offline"})
    public Result offline(@RequestBody StudentClientEntity request) {
        studentClientService.offline(request);
        return Result.success("student client offline status registered");
    }

    @GetMapping({"/student-clients", "/api/student-clients"})
    public Result list(@RequestParam Long labId) {
        return Result.success("student client list loaded", studentClientService.listByLabId(labId));
    }
}
