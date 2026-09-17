package com.parkease.service;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.parkease.dto.CheckInRequest;
import com.parkease.dto.CheckInResponse;
import com.parkease.dto.CheckOutResponse;
import com.parkease.entity.ParkingSession;
import com.parkease.entity.ParkingSpot;
import com.parkease.entity.SessionStatus;
import com.parkease.entity.SpotType;
import com.parkease.entity.VehicleType;
import com.parkease.exception.BadRequestException;
import com.parkease.exception.ConflictException;
import com.parkease.exception.NotFoundException;
import com.parkease.repository.ParkingSessionRepository;
import com.parkease.repository.ParkingSpotRepository;

@Service
public class ParkingService {

    private final ParkingSessionRepository parkingSessionRepository;
    private final ParkingSpotRepository parkingSpotRepository;
    private final FeeCalculatorService feeCalculatorService;

    public ParkingService(ParkingSessionRepository parkingSessionRepository,
                          ParkingSpotRepository parkingSpotRepository,
                          FeeCalculatorService feeCalculatorService) {
        this.parkingSessionRepository = parkingSessionRepository;
        this.parkingSpotRepository = parkingSpotRepository;
        this.feeCalculatorService = feeCalculatorService;
    }

    @Transactional
    public CheckInResponse checkIn(CheckInRequest request) {
        if (request == null || request.getPlateNumber() == null
                || request.getPlateNumber().isBlank() || request.getVehicleType() == null) {
            throw new BadRequestException("Plate number and vehicle type are required");
        }

        String plateNumber = request.getPlateNumber().trim().toUpperCase();
        VehicleType vehicleType = request.getVehicleType();

        if (parkingSessionRepository.findFirstByPlateNumberIgnoreCaseAndStatus(
                plateNumber, SessionStatus.ACTIVE).isPresent()) {
            throw new ConflictException("Vehicle already has an active parking session");
        }

        ParkingSpot parkingSpot = findAvailableSpot(vehicleType);
        parkingSpot.setOccupied(true);
        parkingSpotRepository.save(parkingSpot);

        LocalDateTime checkInTime = LocalDateTime.now();
        ParkingSession parkingSession = parkingSessionRepository.save(new ParkingSession(
                plateNumber, vehicleType, parkingSpot, checkInTime, SessionStatus.ACTIVE));

        return new CheckInResponse(
                parkingSession.getId(),
                parkingSession.getPlateNumber(),
                parkingSession.getVehicleType(),
                parkingSpot.getSpotNumber(),
                parkingSpot.getFloor(),
                parkingSession.getCheckInTime(),
                parkingSession.getStatus());
    }

            @Transactional
            public CheckOutResponse checkOut(Long sessionId) {
            ParkingSession parkingSession = parkingSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Parking session not found: " + sessionId));

            if (parkingSession.getStatus() != SessionStatus.ACTIVE) {
                throw new ConflictException("Parking session is already completed");
            }

            LocalDateTime checkOutTime = LocalDateTime.now();
            parkingSession.setCheckOutTime(checkOutTime);
            parkingSession.setFee(feeCalculatorService.calculateFee(
                parkingSession.getCheckInTime(), checkOutTime));
            parkingSession.setStatus(SessionStatus.COMPLETED);

            ParkingSpot parkingSpot = parkingSession.getParkingSpot();
            parkingSpot.setOccupied(false);
            parkingSessionRepository.save(parkingSession);
            parkingSpotRepository.save(parkingSpot);

            return new CheckOutResponse(
                parkingSession.getId(),
                parkingSession.getPlateNumber(),
                parkingSpot.getSpotNumber(),
                parkingSession.getCheckInTime(),
                parkingSession.getCheckOutTime(),
                Duration.between(parkingSession.getCheckInTime(), checkOutTime).toMinutes(),
                parkingSession.getFee(),
                parkingSession.getStatus());
            }

    private ParkingSpot findAvailableSpot(VehicleType vehicleType) {
        List<SpotType> preferredTypes = switch (vehicleType) {
            case COMPACT -> List.of(SpotType.COMPACT, SpotType.STANDARD);
            case STANDARD -> List.of(SpotType.STANDARD);
            case EV -> List.of(SpotType.EV);
        };

        return preferredTypes.stream()
                .map(parkingSpotRepository::findByOccupiedFalseAndType)
                .flatMap(List::stream)
                .findFirst()
                .orElseThrow(() -> new ConflictException("No compatible parking spot is available"));
    }
}