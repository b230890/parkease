package com.parkease.service;

import java.time.LocalDateTime;
import java.time.Duration;
import java.time.Clock;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.parkease.dto.CheckInRequest;
import com.parkease.dto.CheckInResponse;
import com.parkease.dto.CheckOutResponse;
import com.parkease.dto.ClockResponse;
import com.parkease.dto.ParkingSessionResponse;
import com.parkease.entity.RateCard;
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
    private final RateCardService rateCardService;
    private final Clock clock;

    public ParkingService(ParkingSessionRepository parkingSessionRepository,
                          ParkingSpotRepository parkingSpotRepository,
                          FeeCalculatorService feeCalculatorService,
                          RateCardService rateCardService) {
                this(parkingSessionRepository, parkingSpotRepository, feeCalculatorService,
                    rateCardService, Clock.systemDefaultZone());
                }

                public ParkingService(ParkingSessionRepository parkingSessionRepository,
                          ParkingSpotRepository parkingSpotRepository,
                          FeeCalculatorService feeCalculatorService,
                          RateCardService rateCardService,
                          Clock clock) {
        this.parkingSessionRepository = parkingSessionRepository;
        this.parkingSpotRepository = parkingSpotRepository;
        this.feeCalculatorService = feeCalculatorService;
        this.rateCardService = rateCardService;
                this.clock = clock;
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

            ParkingSpot parkingSpot = parkingSession.getParkingSpot();
            if (parkingSpot == null) {
                throw new NotFoundException("Parking spot is missing for session " + sessionId);
            }
            LocalDateTime checkOutTime = LocalDateTime.now(clock);
            completeSession(parkingSession, parkingSpot, checkOutTime);

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

    @Transactional
    public ClockResponse autoCloseOverdueSessions() {
        LocalDateTime currentTime = LocalDateTime.now(clock);
        LocalDateTime cutoff = currentTime.minusHours(24);
        List<ClockResponse.AutoClosedSession> closedSessions = new java.util.ArrayList<>();
        int spotsFreed = 0;
        int skippedSessions = 0;

        for (ParkingSession parkingSession : parkingSessionRepository.findByStatus(SessionStatus.ACTIVE)) {
            if (parkingSession.getCheckInTime() == null
                    || !parkingSession.getCheckInTime().isBefore(cutoff)) {
                continue;
            }

            ParkingSpot parkingSpot = parkingSession.getParkingSpot();
            if (parkingSpot == null) {
                skippedSessions++;
                continue;
            }

            completeSession(parkingSession, parkingSpot, currentTime);
            closedSessions.add(new ClockResponse.AutoClosedSession(
                    parkingSession.getId(), parkingSession.getPlateNumber(),
                    currentTime, parkingSession.getFee()));
            spotsFreed++;
        }

        return new ClockResponse(closedSessions.size(), spotsFreed, skippedSessions, closedSessions);
    }

    private void completeSession(ParkingSession parkingSession, ParkingSpot parkingSpot,
                                 LocalDateTime checkOutTime) {
        RateCard rateCard = rateCardService.getActiveRate(parkingSpot.getType());
        parkingSession.setCheckOutTime(checkOutTime);
        parkingSession.setFee(feeCalculatorService.calculateFee(
                parkingSession.getCheckInTime(), checkOutTime, rateCard));
        parkingSession.setAppliedFirstHourRate(rateCard.getFirstHourRate());
        parkingSession.setAppliedAdditionalHourRate(rateCard.getAdditionalHourRate());
        parkingSession.setAppliedDailyCap(rateCard.getDailyCap());
        parkingSession.setStatus(SessionStatus.COMPLETED);
        parkingSpot.setOccupied(false);
        parkingSessionRepository.save(parkingSession);
        parkingSpotRepository.save(parkingSpot);
    }

    @Transactional(readOnly = true)
    public List<ParkingSessionResponse> searchByPlateNumber(String plateNumber) {
        if (plateNumber == null || plateNumber.isBlank()) {
            throw new BadRequestException("Plate number is required");
        }

        return parkingSessionRepository.findByPlateNumberIgnoreCase(
                        plateNumber.trim().toUpperCase())
                .stream()
                .map(this::toParkingSessionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ParkingSessionResponse> findActiveSessions() {
        return parkingSessionRepository.findByStatus(SessionStatus.ACTIVE)
                .stream()
                .map(this::toParkingSessionResponse)
                .toList();
    }

    private ParkingSessionResponse toParkingSessionResponse(ParkingSession parkingSession) {
        ParkingSpot parkingSpot = parkingSession.getParkingSpot();
        return new ParkingSessionResponse(
                parkingSession.getId(),
                parkingSession.getPlateNumber(),
                parkingSession.getVehicleType(),
                parkingSpot.getSpotNumber(),
                parkingSpot.getFloor(),
                parkingSession.getCheckInTime(),
                parkingSession.getCheckOutTime(),
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