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
        return Result.success("\u83b7\u53d6\u9884\u7ea6\u5217\u8868\u6210\u529f", reservationsService.getReservations());
    }

    @PostMapping("/reservations")
    public Result insertReservation(@RequestBody ReservationsEntity reservationsEntity) {
        reservationsService.insertReservation(reservationsEntity);
        return Result.success("\u65b0\u589e\u9884\u7ea6\u6210\u529f", reservationsEntity);
    }

    @GetMapping("/public/labs/{id:\\d+}/reservation-profile")
    public Result getScanReservationProfile(
            @PathVariable("id") Integer id,
            @RequestParam(value = "code", required = false) String code
    ) {
        return Result.success("\u83b7\u53d6\u626b\u7801\u9884\u7ea6\u8d44\u6599\u6210\u529f", reservationsService.getScanReservationProfile(id, code));
    }

    @PostMapping("/public/reservations/scan")
    public Result submitScanReservation(@RequestBody ReservationsEntity reservationsEntity) {
        return Result.success("\u63d0\u4ea4\u626b\u7801\u9884\u7ea6\u6210\u529f", reservationsService.submitScanReservation(reservationsEntity));
    }

    @GetMapping("/public/reservations/{id:\\d+}/status")
    public Result getScanReservationStatus(
            @PathVariable("id") Integer id,
            @RequestParam(value = "contact", required = false) String contact
    ) {
        return Result.success("\u83b7\u53d6\u626b\u7801\u9884\u7ea6\u72b6\u6001\u6210\u529f", reservationsService.getScanReservationStatus(id, contact));
    }

    @GetMapping("/reservations/{id:\\d+}")
    public Result getReservation(@PathVariable("id") Integer id) {
        return Result.success("\u83b7\u53d6\u9884\u7ea6\u8be6\u60c5\u6210\u529f", reservationsService.getReservation(id));
    }

    @PutMapping("/reservations/{id:\\d+}")
    public Result updateReservation(@PathVariable("id") Integer id, @RequestBody ReservationsEntity reservationsEntity) {
        reservationsService.updateReservation(id, reservationsEntity);
        return Result.success("\u66f4\u65b0\u9884\u7ea6\u6210\u529f", reservationsService.getReservation(id));
    }

    @PutMapping("/reservations/batch")
    public Result replaceReservations(@RequestBody ReservationBatchRequest request) {
        List<ReservationsEntity> reservations = request == null || request.getRecords() == null
                ? Collections.emptyList()
                : request.getRecords();
        return Result.success("\u6279\u91cf\u4fdd\u5b58\u9884\u7ea6\u6210\u529f", reservationsService.replaceReservations(reservations));
    }

    @DeleteMapping("/reservations/{id:\\d+}")
    public Result deleteReservation(@PathVariable("id") Integer id) {
        reservationsService.deleteReservation(id);
        return Result.success("\u5220\u9664\u9884\u7ea6\u6210\u529f");
    }

    @PostMapping("/reservations/reset")
    public Result resetReservations() {
        return Result.success("\u91cd\u7f6e\u9884\u7ea6\u6570\u636e\u6210\u529f", reservationsService.getReservations());
    }

    @PostMapping("/reservations/{id:\\d+}/approve")
    public Result getApproved(@PathVariable("id") Integer id, @RequestBody(required = false) ReservationsEntity reservationsEntity) {
        return Result.success("\u901a\u8fc7\u9884\u7ea6\u6210\u529f", reservationsService.getApproved(id, reservationsEntity == null ? new ReservationsEntity() : reservationsEntity));
    }

    @PostMapping("/reservations/{id:\\d+}/reject")
    public Result getRejected(@PathVariable("id") Integer id, @RequestBody(required = false) ReservationsEntity reservationsEntity) {
        return Result.success("\u9a73\u56de\u9884\u7ea6\u6210\u529f", reservationsService.getRejected(id, reservationsEntity == null ? new ReservationsEntity() : reservationsEntity));
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
