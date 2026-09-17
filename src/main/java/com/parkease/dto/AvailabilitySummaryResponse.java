package com.parkease.dto;

public record AvailabilitySummaryResponse(
        long totalSpots,
        long occupiedSpots,
        long availableSpots,
        long totalCompactSpots,
        long availableCompactSpots,
        long totalStandardSpots,
        long availableStandardSpots,
        long totalEvSpots,
        long availableEvSpots) {
}