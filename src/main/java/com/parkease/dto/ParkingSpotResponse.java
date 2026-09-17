package com.parkease.dto;

import com.parkease.entity.SpotType;

public record ParkingSpotResponse(
        Long id,
        String spotNumber,
        Integer floor,
        SpotType type,
        boolean occupied) {
}