package com.parkease.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.parkease.entity.ParkingSession;
import com.parkease.entity.SessionStatus;

public interface ParkingSessionRepository extends JpaRepository<ParkingSession, Long> {

    Optional<ParkingSession> findFirstByPlateNumberIgnoreCaseAndStatus(
            String plateNumber, SessionStatus status);

    List<ParkingSession> findByPlateNumberIgnoreCase(String plateNumber);

    List<ParkingSession> findByStatus(SessionStatus status);

    Page<ParkingSession> findByStatus(SessionStatus status, Pageable pageable);
}
