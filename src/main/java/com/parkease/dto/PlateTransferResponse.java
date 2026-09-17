package com.parkease.dto;

import java.time.LocalDateTime;

import com.parkease.entity.SessionStatus;

public record PlateTransferResponse(
        Long sessionId,
        String oldPlateNumber,
        String newPlateNumber,
        String spotNumber,
        LocalDateTime checkInTime,
        SessionStatus status) {
}