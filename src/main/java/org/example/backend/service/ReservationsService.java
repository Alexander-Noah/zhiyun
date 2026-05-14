package org.example.backend.service;

import org.example.backend.entity.ReservationsEntity;

import java.util.List;
import java.util.Map;

public interface ReservationsService {

    Object getReservations();

    void insertReservation(ReservationsEntity reservationsEntity);

    Object getReservation(Integer id);

    void updateReservation(Integer id, ReservationsEntity reservationsEntity);

    void deleteReservation(Integer id);

    List<ReservationsEntity> replaceReservations(List<ReservationsEntity> reservations);

    ReservationsEntity getApproved(Integer id, ReservationsEntity reservationsEntity);

    ReservationsEntity getRejected(Integer id, ReservationsEntity reservationsEntity);

    Map<String, Object> getScanReservationProfile(Integer labId, String labCode);

    ReservationsEntity submitScanReservation(ReservationsEntity reservationsEntity);

    Map<String, Object> getScanReservationStatus(Integer id, String contact);
}
