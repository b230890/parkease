package com.parkease.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.parkease.dto.AvailabilitySummaryResponse;
import com.parkease.dto.ParkingSpotResponse;
import com.parkease.dto.SpotTypeAvailabilityResponse;
import com.parkease.service.ParkingSpotService;

@RestController
@RequestMapping("/api/spots")
public class ParkingSpotController {

    private final ParkingSpotService parkingSpotService;

    public ParkingSpotController(ParkingSpotService parkingSpotService) {
        this.parkingSpotService = parkingSpotService;
    }

    @GetMapping
    public List<ParkingSpotResponse> findAll() {
        return parkingSpotService.findAll();
    }

    @GetMapping("/availability")
    public AvailabilitySummaryResponse availability() {
        return parkingSpotService.getSummary();
    }

    @GetMapping(value = "/availability", params = "type")
    public SpotTypeAvailabilityResponse availabilityByType(@RequestParam String type) {
        return parkingSpotService.getAvailabilityByType(type);
    }
}