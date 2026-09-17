package com.parkease.dto;

import java.util.List;

public record ParkingHistoryResponse(
        List<ParkingSessionResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}