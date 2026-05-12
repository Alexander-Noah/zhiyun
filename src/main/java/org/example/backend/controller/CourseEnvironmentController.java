package org.example.backend.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.backend.result.Result;
import org.example.backend.entity.CourseEnvironmentEntity;
import org.example.backend.service.CourseEnvironmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RestController
@Slf4j
public class CourseEnvironmentController {

    @Autowired
    private CourseEnvironmentService courseEnvironmentService;

    /*
    *获取课程环境需求列表
     */
    @GetMapping("/course-environment-requests")
    public Result getEnvironment(){
        log.info("获取课程环境需求列表");
        return Result.success(courseEnvironmentService.getEnvironment());
    }
    @PostMapping("/course-environment-requests")
    public Result InserterCourseEnvironment(@RequestBody CourseEnvironmentEntity courseEnvironment){
        log.info("插入课程环境需求");
        return Result.success(courseEnvironmentService.InserterCourseEnvironment(courseEnvironment));
    }

    @GetMapping("/course-environment-requests/{id}")
    public Result getCourseEnvironment(@PathVariable Integer id){
        log.info("获取课程环境需求");
        return Result.success(courseEnvironmentService.getCourseEnvironment(id));
    }
    @PutMapping("/course-environment-requests/{id}")
    public Result updateCourseEnvironment(@PathVariable Integer id, @RequestBody CourseEnvironmentEntity courseEnvironment){
        log.info("更新课程环境需求");
        return Result.success(courseEnvironmentService.updateCourseEnvironment(id,courseEnvironment));
    }

    @DeleteMapping("/course-environment-requests/{id}")
    public Result deleteCourseEnvironment(@PathVariable Integer id){
        log.info("删除课程环境需求");
        return Result.success(courseEnvironmentService.deleteCourseEnvironment(id));
    }
    @PostMapping("/course-environment-requests/{id}/confirm")
    public Result confirmCourseEnvironment(@PathVariable Integer id){
        log.info("确认课程环境需求");
        return Result.success(courseEnvironmentService.confirmCourseEnvironment(id));
    }
}
