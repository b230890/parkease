package com.parkease.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.parkease.dto.CheckInRequest;
import com.parkease.dto.CheckInResponse;
import com.parkease.entity.ParkingSession;
import com.parkease.entity.ParkingSpot;
import com.parkease.entity.SessionStatus;
import com.parkease.entity.SpotType;
import com.parkease.entity.VehicleType;
import com.parkease.exception.BadRequestException;
import com.parkease.exception.ConflictException;
import com.parkease.repository.ParkingSessionRepository;
import com.parkease.repository.ParkingSpotRepository;

@Service
public class ParkingService {

    private final ParkingSessionRepository parkingSessionRepository;
    private final ParkingSpotRepository parkingSpotRepository;

    public ParkingService(ParkingSessionRepository parkingSessionRepository,
                          ParkingSpotRepository parkingSpotRepository) {
        this.parkingSessionRepository = parkingSessionRepository;
        this.parkingSpotRepository = parkingSpotRepository;
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