package org.example.backend.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.backend.result.Result;
import org.example.backend.entity.softwareEntity;
import org.example.backend.service.softwareService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RestController
@Slf4j
public class softwareController {
    @Autowired
    private softwareService softwareService;

    @GetMapping("/lab-software")
    public Result getLabSoftware(){
        log.info("获取软件环境列表");
        return softwareService.getLabSoftware();
    }

    @PostMapping("/lab-software")
    public Result InserterLabSoftware(@RequestBody softwareEntity software){
        log.info("添加软件环境");
        return softwareService.InserterLabSoftware(software);
    }

    @PutMapping("/lab-software/{id}")
    public Result updateLabSoftware(@PathVariable Long id, @RequestBody softwareEntity software){
        log.info("修改软件环境: {}", id);
        return softwareService.updateLabSoftware(id, software);
    }

    @DeleteMapping("/lab-software/{id}")
    public Result deleteLabSoftware(@PathVariable Long id){
        log.info("删除软件环境: {}", id);
        return softwareService.deleteLabSoftware(id);
    }
}
