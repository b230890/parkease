package com.parkease.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.parkease.dto.ClockResponse;
import com.parkease.service.ParkingService;

@RestController
public class ClockController {

    private final ParkingService parkingService;

    public ClockController(ParkingService parkingService) {
        this.parkingService = parkingService;
    }

    @PostMapping("/clock")
    public ClockResponse runNightlyAutomation() {
        return parkingService.autoCloseOverdueSessions();
    }
}