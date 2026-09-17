package com.parkease.dto;

import java.time.LocalDateTime;

import com.parkease.entity.SessionStatus;
import com.parkease.entity.VehicleType;

public record CheckInResponse(
        Long sessionId,
        String plateNumber,
        VehicleType vehicleType,
        String spotNumber,
        Integer floor,
        LocalDateTime checkInTime,
        SessionStatus status) {
}