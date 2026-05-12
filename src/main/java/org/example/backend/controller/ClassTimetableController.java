package org.example.backend.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.backend.result.Result;
import org.example.backend.service.ClassTimetableService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@CrossOrigin
@RestController
@Slf4j
public class ClassTimetableController {
    private final ClassTimetableService classTimetableService;

    public ClassTimetableController(ClassTimetableService classTimetableService) {
        this.classTimetableService = classTimetableService;
    }

    @GetMapping("/class-timetables")
    public Result listTimetables(
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) String teacher,
            @RequestParam(required = false) String className,
            @RequestParam(required = false) String classroom,
            @RequestParam(required = false) String courseName,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer week,
            @RequestParam(required = false) Integer limit
    ) {
        return Result.success(classTimetableService.listTimetables(
                semester,
                teacher,
                className,
                classroom,
                courseName,
                keyword,
                week,
                limit
        ));
    }

    @GetMapping("/class-timetables/summary")
    public Result getSummary() {
        return Result.success(classTimetableService.getSummary());
    }

    @GetMapping("/class-timetables/semesters")
    public Result listSemesters() {
        return Result.success(classTimetableService.listSemesters());
    }

    @PostMapping("/class-timetables/crawl")
    public Result triggerCrawler() {
        log.info("触发课表爬虫脚本");
        return Result.success(classTimetableService.triggerCrawler());
    }

    @GetMapping("/class-timetables/credential")
    public Result getCrawlerCredential() {
        return Result.success(classTimetableService.getCrawlerCredential());
    }

    @PostMapping("/class-timetables/credential")
    public Result saveCrawlerCredential(@RequestBody Map<String, String> payload) {
        log.info("保存课表爬虫教务账号");
        return Result.success(classTimetableService.saveCrawlerCredential(payload));
    }

    @DeleteMapping("/class-timetables/credential")
    public Result deleteCrawlerCredential() {
        log.info("删除课表爬虫教务账号");
        classTimetableService.deleteCrawlerCredential();
        return Result.success(true);
    }
}
