package com.parkease.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.parkease.entity.SessionStatus;

public record CheckOutResponse(
        Long sessionId,
        String plateNumber,
        String spotNumber,
        LocalDateTime checkInTime,
        LocalDateTime checkOutTime,
        long durationMinutes,
        BigDecimal fee,
        SessionStatus status) {
}