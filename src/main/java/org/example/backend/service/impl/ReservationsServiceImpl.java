package org.example.backend.service.impl;

import org.example.backend.entity.ReservationsEntity;
import org.example.backend.mapper.ReservationsMapper;
import org.example.backend.service.BusinessLoopService;
import org.example.backend.service.ReservationsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ReservationsServiceImpl implements ReservationsService {
    private final ReservationsMapper reservationsMapper;
    private final BusinessLoopService businessLoopService;

    public ReservationsServiceImpl(ReservationsMapper reservationsMapper, BusinessLoopService businessLoopService) {
        this.reservationsMapper = reservationsMapper;
        this.businessLoopService = businessLoopService;
    }

    @Override
    public Object getReservations() {
        return reservationsMapper.getReservations();
    }

    @Override
    @Transactional
    public void insertReservation(ReservationsEntity reservation) {
        normalizeReservation(null, reservation);
        reservationsMapper.insertReservation(reservation);
        businessLoopService.recordEvent("reservation", "create", reservation.getLab(), reservation.getStatus(), Map.of(
                "reservationId", reservation.getId(),
                "applicant", reservation.getApplicant()
        ));
    }

    @Override
    public Object getReservation(Integer id) {
        return reservationsMapper.getReservation(id);
    }

    @Override
    @Transactional
    public void updateReservation(Integer id, ReservationsEntity reservation) {
        normalizeReservation(id, reservation);
        int updatedCount = reservationsMapper.updateReservation(id, reservation);
        if (updatedCount == 0) {
            throw new IllegalArgumentException("预约记录不存在");
        }

        ReservationsEntity updatedReservation = reservationsMapper.getReservation(id);
        businessLoopService.recordEvent("reservation", "update", updatedReservation.getLab(), updatedReservation.getStatus(), Map.of(
                "reservationId", id
        ));
    }

    @Override
    public void deleteReservation(Integer id) {
        reservationsMapper.deleteReservation(id);
        businessLoopService.recordEvent("reservation", "delete", String.valueOf(id), "已删除", Map.of("reservationId", id));
    }

    @Override
    @Transactional
    public List<ReservationsEntity> replaceReservations(List<ReservationsEntity> reservations) {
        if (reservations == null || reservations.isEmpty()) {
            return reservationsMapper.getReservations();
        }

        reservationsMapper.deleteAllReservations();
        for (ReservationsEntity reservation : reservations) {
            normalizeReservation(null, reservation);
            reservationsMapper.insertReservation(reservation);
        }
        businessLoopService.recordEvent("reservation", "batch-save", "预约台账", "已同步", Map.of("count", reservations.size()));

        return reservationsMapper.getReservations();
    }

    @Override
    @Transactional
    public ReservationsEntity getApproved(Integer id, ReservationsEntity reservationPatch) {
        int updatedCount = reservationsMapper.getApproved(id, reservationPatch);
        if (updatedCount == 0) {
            throw new IllegalArgumentException("预约记录不存在");
        }
        ReservationsEntity reservation = reservationsMapper.getReservation(id);
        businessLoopService.syncUsageRecordAfterReservationApproved(reservation);
        return reservation;
    }

    @Override
    @Transactional
    public ReservationsEntity getRejected(Integer id, ReservationsEntity reservationPatch) {
        int updatedCount = reservationsMapper.getRejected(id, reservationPatch);
        if (updatedCount == 0) {
            throw new IllegalArgumentException("预约记录不存在");
        }
        ReservationsEntity reservation = reservationsMapper.getReservation(id);
        businessLoopService.recordEvent("reservation", "reject", reservation.getLab(), reservation.getStatus(), Map.of(
                "reservationId", id,
                "reason", firstNonBlank(reservation.getNote(), "驳回")
        ));
        return reservation;
    }

    private void normalizeReservation(Integer id, ReservationsEntity reservation) {
        if (reservation.getStatus() == null || reservation.getStatus().isBlank()) {
            reservation.setStatus("待审核");
        }
        if (reservation.getReviewerName() == null || reservation.getReviewerName().isBlank()) {
            reservation.setReviewerName("待审核");
        }

        reservation.setConflict(reservationsMapper.countConflictingReservations(id, reservation) > 0);
        if (Boolean.TRUE.equals(reservation.getConflict()) && "待审核".equals(reservation.getStatus())) {
            reservation.setStatus("冲突");
            if (reservation.getReviewRemark() == null || reservation.getReviewRemark().isBlank()) {
                reservation.setReviewRemark("系统检测到同实验室同日期同时间段预约冲突");
            }
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
