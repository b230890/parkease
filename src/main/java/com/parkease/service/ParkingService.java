package com.parkease.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.parkease.dto.CheckInRequest;
import com.parkease.dto.CheckInResponse;
import com.parkease.dto.CheckOutResponse;
import com.parkease.dto.ClockResponse;
import com.parkease.dto.ParkingHistoryResponse;
import com.parkease.dto.ParkingSessionResponse;
import com.parkease.dto.PlateTransferRequest;
import com.parkease.dto.PlateTransferResponse;
import com.parkease.entity.ParkingSession;
import com.parkease.entity.ParkingSpot;
import com.parkease.entity.RateCard;
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

    private static final int MAX_HISTORY_PAGE_SIZE = 100;

    private static final Set<String> HISTORY_SORT_FIELDS = Set.of(
            "checkInTime",
            "checkOutTime",
            "fee",
            "plateNumber"
    );

    private final ParkingSessionRepository parkingSessionRepository;
    private final ParkingSpotRepository parkingSpotRepository;
    private final FeeCalculatorService feeCalculatorService;
    private final RateCardService rateCardService;
    private final Clock clock;

    // Used by existing unit tests.
    public ParkingService(
            ParkingSessionRepository parkingSessionRepository,
            ParkingSpotRepository parkingSpotRepository,
            FeeCalculatorService feeCalculatorService,
            RateCardService rateCardService) {

        this(
                parkingSessionRepository,
                parkingSpotRepository,
                feeCalculatorService,
                rateCardService,
                Clock.systemDefaultZone()
        );
    }

    // Used by Spring and by tests that need a controllable Clock.
    @Autowired
    public ParkingService(
            ParkingSessionRepository parkingSessionRepository,
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

        if (request == null
                || request.getPlateNumber() == null
                || request.getPlateNumber().isBlank()
                || request.getVehicleType() == null) {

            throw new BadRequestException(
                    "Plate number and vehicle type are required");
        }

        String plateNumber =
                request.getPlateNumber().trim().toUpperCase();

        VehicleType vehicleType = request.getVehicleType();

        if (parkingSessionRepository
                .findFirstByPlateNumberIgnoreCaseAndStatus(
                        plateNumber,
                        SessionStatus.ACTIVE)
                .isPresent()) {

            throw new ConflictException(
                    "Vehicle already has an active parking session");
        }

        ParkingSpot parkingSpot =
                findAvailableSpot(vehicleType);

        parkingSpot.setOccupied(true);
        parkingSpotRepository.save(parkingSpot);

        LocalDateTime checkInTime =
                LocalDateTime.now(clock);

        ParkingSession parkingSession =
                parkingSessionRepository.save(
                        new ParkingSession(
                                plateNumber,
                                vehicleType,
                                parkingSpot,
                                checkInTime,
                                SessionStatus.ACTIVE
                        )
                );

        return new CheckInResponse(
                parkingSession.getId(),
                parkingSession.getPlateNumber(),
                parkingSession.getVehicleType(),
                parkingSpot.getSpotNumber(),
                parkingSpot.getFloor(),
                parkingSession.getCheckInTime(),
                parkingSession.getStatus()
        );
    }

    @Transactional
    public PlateTransferResponse transferPlate(
            PlateTransferRequest request) {

        if (request == null
                || request.getOldPlateNumber() == null
                || request.getOldPlateNumber().isBlank()
                || request.getNewPlateNumber() == null
                || request.getNewPlateNumber().isBlank()) {

            throw new BadRequestException(
                    "Both old and new plate numbers are required");
        }

        String oldPlateNumber =
                normalizePlateNumber(
                        request.getOldPlateNumber());

        String newPlateNumber =
                normalizePlateNumber(
                        request.getNewPlateNumber());

        if (oldPlateNumber.equals(newPlateNumber)) {
            throw new ConflictException(
                    "Old and new plate numbers must be different");
        }

        ParkingSession parkingSession =
                parkingSessionRepository
                        .findFirstByPlateNumberIgnoreCaseAndStatus(
                                oldPlateNumber,
                                SessionStatus.ACTIVE)
                        .orElseGet(() -> {

                            boolean hasCompletedSession =
                                    parkingSessionRepository
                                            .findByPlateNumberIgnoreCase(
                                                    oldPlateNumber)
                                            .stream()
                                            .anyMatch(session ->
                                                    session.getStatus()
                                                            != SessionStatus.ACTIVE);

                            if (hasCompletedSession) {
                                throw new ConflictException(
                                        "Only active parking sessions can be transferred");
                            }

                            throw new NotFoundException(
                                    "Active parking session not found for plate "
                                            + oldPlateNumber);
                        });

        if (parkingSessionRepository
                .findFirstByPlateNumberIgnoreCaseAndStatus(
                        newPlateNumber,
                        SessionStatus.ACTIVE)
                .isPresent()) {

            throw new ConflictException(
                    "New plate already has an active parking session");
        }

        String originalPlateNumber =
                parkingSession.getPlateNumber();

        ParkingSpot parkingSpot =
                parkingSession.getParkingSpot();

        if (parkingSpot == null) {
            throw new NotFoundException(
                    "Parking spot is missing for session "
                            + parkingSession.getId());
        }

        parkingSession.setPlateNumber(newPlateNumber);
        parkingSessionRepository.save(parkingSession);

        return new PlateTransferResponse(
                parkingSession.getId(),
                originalPlateNumber,
                newPlateNumber,
                parkingSpot.getSpotNumber(),
                parkingSession.getCheckInTime(),
                parkingSession.getStatus()
        );
    }

    @Transactional
    public CheckOutResponse checkOut(Long sessionId) {

        ParkingSession parkingSession =
                parkingSessionRepository.findById(sessionId)
                        .orElseThrow(() ->
                                new NotFoundException(
                                        "Parking session not found: "
                                                + sessionId));

        if (parkingSession.getStatus()
                != SessionStatus.ACTIVE) {

            throw new ConflictException(
                    "Parking session is already completed");
        }

        ParkingSpot parkingSpot =
                parkingSession.getParkingSpot();

        if (parkingSpot == null) {
            throw new NotFoundException(
                    "Parking spot is missing for session "
                            + sessionId);
        }

        LocalDateTime checkOutTime =
                LocalDateTime.now(clock);

        completeSession(
                parkingSession,
                parkingSpot,
                checkOutTime);

        return new CheckOutResponse(
                parkingSession.getId(),
                parkingSession.getPlateNumber(),
                parkingSpot.getSpotNumber(),
                parkingSession.getCheckInTime(),
                parkingSession.getCheckOutTime(),
                Duration.between(
                        parkingSession.getCheckInTime(),
                        checkOutTime).toMinutes(),
                parkingSession.getFee(),
                parkingSession.getStatus()
        );
    }

    @Transactional
    public ClockResponse autoCloseOverdueSessions() {

        LocalDateTime currentTime =
                LocalDateTime.now(clock);

        LocalDateTime cutoff =
                currentTime.minusHours(24);

        List<ClockResponse.AutoClosedSession> closedSessions =
                new java.util.ArrayList<>();

        int spotsFreed = 0;
        int skippedSessions = 0;

        for (ParkingSession parkingSession :
                parkingSessionRepository.findByStatus(
                        SessionStatus.ACTIVE)) {

            if (parkingSession.getCheckInTime() == null
                    || !parkingSession.getCheckInTime()
                            .isBefore(cutoff)) {

                continue;
            }

            ParkingSpot parkingSpot =
                    parkingSession.getParkingSpot();

            if (parkingSpot == null) {
                skippedSessions++;
                continue;
            }

            completeSession(
                    parkingSession,
                    parkingSpot,
                    currentTime);

            closedSessions.add(
                    new ClockResponse.AutoClosedSession(
                            parkingSession.getId(),
                            parkingSession.getPlateNumber(),
                            currentTime,
                            parkingSession.getFee()
                    )
            );

            spotsFreed++;
        }

        return new ClockResponse(
                closedSessions.size(),
                spotsFreed,
                skippedSessions,
                closedSessions
        );
    }

    private void completeSession(
            ParkingSession parkingSession,
            ParkingSpot parkingSpot,
            LocalDateTime checkOutTime) {

        RateCard rateCard =
                rateCardService.getActiveRate(
                        parkingSpot.getType());

        parkingSession.setCheckOutTime(checkOutTime);

        parkingSession.setFee(
                feeCalculatorService.calculateFee(
                        parkingSession.getCheckInTime(),
                        checkOutTime,
                        rateCard
                )
        );

        parkingSession.setAppliedFirstHourRate(
                rateCard.getFirstHourRate());

        parkingSession.setAppliedAdditionalHourRate(
                rateCard.getAdditionalHourRate());

        parkingSession.setAppliedDailyCap(
                rateCard.getDailyCap());

        parkingSession.setStatus(
                SessionStatus.COMPLETED);

        parkingSpot.setOccupied(false);

        parkingSessionRepository.save(
                parkingSession);

        parkingSpotRepository.save(
                parkingSpot);
    }

    @Transactional(readOnly = true)
    public List<ParkingSessionResponse> searchByPlateNumber(
            String plateNumber) {

        if (plateNumber == null
                || plateNumber.isBlank()) {

            throw new BadRequestException(
                    "Plate number is required");
        }

        return parkingSessionRepository
                .findByPlateNumberIgnoreCase(
                        plateNumber.trim().toUpperCase())
                .stream()
                .map(this::toParkingSessionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ParkingSessionResponse> findActiveSessions() {

        return parkingSessionRepository
                .findByStatus(SessionStatus.ACTIVE)
                .stream()
                .map(this::toParkingSessionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ParkingHistoryResponse findHistory(
            Pageable pageable) {

        validateHistoryPageable(pageable);

        Page<ParkingSession> history =
                parkingSessionRepository.findByStatus(
                        SessionStatus.COMPLETED,
                        pageable);

        return new ParkingHistoryResponse(
                history.map(this::toParkingSessionResponse)
                        .getContent(),
                history.getNumber(),
                history.getSize(),
                history.getTotalElements(),
                history.getTotalPages()
        );
    }

    private void validateHistoryPageable(
            Pageable pageable) {

        if (pageable == null
                || pageable.getPageNumber() < 0) {

            throw new BadRequestException(
                    "Page number must not be negative");
        }

        if (pageable.getPageSize() < 1
                || pageable.getPageSize()
                > MAX_HISTORY_PAGE_SIZE) {

            throw new BadRequestException(
                    "Page size must be between 1 and "
                            + MAX_HISTORY_PAGE_SIZE);
        }

        if (pageable.getSort().isUnsorted()
                || pageable.getSort()
                        .stream()
                        .anyMatch(order ->
                                !HISTORY_SORT_FIELDS.contains(
                                        order.getProperty()))) {

            throw new BadRequestException(
                    "History sort must use checkInTime, "
                            + "checkOutTime, fee, or plateNumber");
        }
    }

    private ParkingSessionResponse toParkingSessionResponse(
            ParkingSession parkingSession) {

        ParkingSpot parkingSpot =
                parkingSession.getParkingSpot();

        return new ParkingSessionResponse(
                parkingSession.getId(),
                parkingSession.getPlateNumber(),
                parkingSession.getVehicleType(),
                parkingSpot.getSpotNumber(),
                parkingSpot.getFloor(),
                parkingSession.getCheckInTime(),
                parkingSession.getCheckOutTime(),
                parkingSession.getFee(),
                parkingSession.getStatus()
        );
    }

    private ParkingSpot findAvailableSpot(
            VehicleType vehicleType) {

        List<SpotType> preferredTypes =
                switch (vehicleType) {

                    case COMPACT ->
                            List.of(
                                    SpotType.COMPACT,
                                    SpotType.STANDARD);

                    case STANDARD ->
                            List.of(SpotType.STANDARD);

                    case EV ->
                            List.of(SpotType.EV);
                };

        return preferredTypes.stream()
                .map(parkingSpotRepository::
                        findByOccupiedFalseAndType)
                .flatMap(List::stream)
                .findFirst()
                .orElseThrow(() ->
                        new ConflictException(
                                "No compatible parking spot is available"));
    }

    private String normalizePlateNumber(
            String plateNumber) {

        return plateNumber.trim().toUpperCase();
    }
}