package com.parkease.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ClockResponse(
        int closedSessions,
        int spotsFreed,
        int skippedSessions,
        List<AutoClosedSession> sessions) {

    public record AutoClosedSession(
            Long sessionId,
            String plateNumber,
            LocalDateTime checkOutTime,
            BigDecimal fee) {
    }
}