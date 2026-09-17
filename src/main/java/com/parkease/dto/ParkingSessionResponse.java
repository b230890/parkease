package com.parkease.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.parkease.entity.SessionStatus;
import com.parkease.entity.VehicleType;

public record ParkingSessionResponse(
        Long sessionId,
        String plateNumber,
        VehicleType vehicleType,
        String spotNumber,
        Integer floor,
        LocalDateTime checkInTime,
        LocalDateTime checkOutTime,
        BigDecimal fee,
        SessionStatus status) {
}