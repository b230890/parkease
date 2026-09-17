package com.parkease.dto;

import com.parkease.entity.SpotType;

public record SpotTypeAvailabilityResponse(
        SpotType type,
        long total,
        long occupied,
        long available) {
}