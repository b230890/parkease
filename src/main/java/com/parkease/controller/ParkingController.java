package com.parkease.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.parkease.dto.CheckInRequest;
import com.parkease.dto.CheckInResponse;
import com.parkease.dto.CheckOutResponse;
import com.parkease.dto.ParkingSessionResponse;
import com.parkease.dto.PlateTransferRequest;
import com.parkease.dto.PlateTransferResponse;
import com.parkease.service.ParkingService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/parking")
public class ParkingController {

    private final ParkingService parkingService;

    public ParkingController(ParkingService parkingService) {
        this.parkingService = parkingService;
    }

    @PostMapping("/check-in")
    public ResponseEntity<CheckInResponse> checkIn(@Valid @RequestBody CheckInRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(parkingService.checkIn(request));
    }

    @PostMapping("/transfer")
    public PlateTransferResponse transfer(@Valid @RequestBody PlateTransferRequest request) {
        return parkingService.transferPlate(request);
    }

    @PostMapping("/check-out/{sessionId}")
    public CheckOutResponse checkOut(@PathVariable Long sessionId) {
        return parkingService.checkOut(sessionId);
    }

    @GetMapping("/search")
    public List<ParkingSessionResponse> search(@RequestParam String plateNumber) {
        return parkingService.searchByPlateNumber(plateNumber);
    }

    @GetMapping("/active")
    public List<ParkingSessionResponse> active() {
        return parkingService.findActiveSessions();
    }
}