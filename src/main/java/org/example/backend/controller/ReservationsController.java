package org.example.backend.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.backend.entity.ReservationsEntity;
import org.example.backend.result.Result;
import org.example.backend.service.ReservationsService;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@CrossOrigin
@RestController
@Slf4j
public class ReservationsController {
    private final ReservationsService reservationsService;

    public ReservationsController(ReservationsService reservationsService) {
        this.reservationsService = reservationsService;
    }

    @GetMapping("/reservations")
    public Result getReservations() {
        return Result.success("获取预约列表成功", reservationsService.getReservations());
    }

    @PostMapping("/reservations")
    public Result insertReservation(@RequestBody ReservationsEntity reservationsEntity) {
        reservationsService.insertReservation(reservationsEntity);
        return Result.success("新增预约成功", reservationsEntity);
    }

    @GetMapping("/public/labs/{id:\\d+}/reservation-profile")
    public Result getScanReservationProfile(
            @PathVariable("id") Integer id,
            @RequestParam(value = "code", required = false) String code
    ) {
        return Result.success("获取扫码预约资料成功", reservationsService.getScanReservationProfile(id, code));
    }

    @PostMapping("/public/reservations/scan")
    public Result submitScanReservation(@RequestBody ReservationsEntity reservationsEntity) {
        return Result.success("提交扫码预约成功", reservationsService.submitScanReservation(reservationsEntity));
    }

    @GetMapping("/public/reservations/{id:\\d+}/status")
    public Result getScanReservationStatus(
            @PathVariable("id") Integer id,
            @RequestParam(value = "contact", required = false) String contact
    ) {
        return Result.success("获取扫码预约状态成功", reservationsService.getScanReservationStatus(id, contact));
    }

    @GetMapping("/reservations/{id:\\d+}")
    public Result getReservation(@PathVariable("id") Integer id) {
        return Result.success("获取预约详情成功", reservationsService.getReservation(id));
    }

    @PutMapping("/reservations/{id:\\d+}")
    public Result updateReservation(@PathVariable("id") Integer id, @RequestBody ReservationsEntity reservationsEntity) {
        reservationsService.updateReservation(id, reservationsEntity);
        return Result.success("更新预约成功", reservationsService.getReservation(id));
    }

    @PutMapping("/reservations/batch")
    public Result replaceReservations(@RequestBody ReservationBatchRequest request) {
        List<ReservationsEntity> reservations = request == null || request.getRecords() == null
                ? Collections.emptyList()
                : request.getRecords();
        return Result.success("批量保存预约成功", reservationsService.replaceReservations(reservations));
    }

    @DeleteMapping("/reservations/{id:\\d+}")
    public Result deleteReservation(@PathVariable("id") Integer id) {
        reservationsService.deleteReservation(id);
        return Result.success("删除预约成功");
    }

    @PostMapping("/reservations/reset")
    public Result resetReservations() {
        return Result.success("重置预约数据成功", reservationsService.getReservations());
    }

    @PostMapping("/reservations/{id:\\d+}/approve")
    public Result getApproved(@PathVariable("id") Integer id, @RequestBody(required = false) ReservationsEntity reservationsEntity) {
        return Result.success("通过预约成功", reservationsService.getApproved(id, reservationsEntity == null ? new ReservationsEntity() : reservationsEntity));
    }

    @PostMapping("/reservations/{id:\\d+}/reject")
    public Result getRejected(@PathVariable("id") Integer id, @RequestBody(required = false) ReservationsEntity reservationsEntity) {
        return Result.success("驳回预约成功", reservationsService.getRejected(id, reservationsEntity == null ? new ReservationsEntity() : reservationsEntity));
    }

    public static class ReservationBatchRequest {
        private String resource;
        private List<ReservationsEntity> records;

        public String getResource() {
            return resource;
        }

        public void setResource(String resource) {
            this.resource = resource;
        }

        public List<ReservationsEntity> getRecords() {
            return records;
        }

        public void setRecords(List<ReservationsEntity> records) {
            this.records = records;
        }
    }
}
