package com.parkease.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.parkease.dto.PlateTransferRequest;
import com.parkease.dto.PlateTransferResponse;
import com.parkease.entity.ParkingSession;
import com.parkease.entity.ParkingSpot;
import com.parkease.entity.RateCard;
import com.parkease.entity.SessionStatus;
import com.parkease.entity.SpotType;
import com.parkease.entity.VehicleType;
import com.parkease.exception.ConflictException;
import com.parkease.exception.NotFoundException;
import com.parkease.repository.ParkingSessionRepository;
import com.parkease.repository.ParkingSpotRepository;

@ExtendWith(MockitoExtension.class)
class PlateTransferTest {

    @Mock
    private ParkingSessionRepository parkingSessionRepository;

    @Mock
    private ParkingSpotRepository parkingSpotRepository;

    @Mock
    private RateCardService rateCardService;

    private ParkingService parkingService;
    private ParkingSpot spot;
    private ParkingSession activeSession;
    private LocalDateTime checkInTime;

    @BeforeEach
    void setUp() {
        parkingService = new ParkingService(parkingSessionRepository, parkingSpotRepository,
                new FeeCalculatorService(), rateCardService);
        checkInTime = LocalDateTime.of(2026, 9, 17, 9, 30);
        spot = new ParkingSpot("C-01", 1, SpotType.COMPACT);
        spot.setOccupied(true);
        activeSession = new ParkingSession("RJ14AB1234", VehicleType.COMPACT, spot,
                checkInTime, SessionStatus.ACTIVE);
        activeSession.setAppliedFirstHourRate(BigDecimal.valueOf(50));
        activeSession.setAppliedAdditionalHourRate(BigDecimal.valueOf(30));
        activeSession.setAppliedDailyCap(BigDecimal.valueOf(200));
    }

    @Test
    void transfersActiveSessionInPlaceAndPreservesParkingState() {
        when(parkingSessionRepository.findFirstByPlateNumberIgnoreCaseAndStatus(
                "RJ14AB1234", SessionStatus.ACTIVE)).thenReturn(Optional.of(activeSession));
        when(parkingSessionRepository.findFirstByPlateNumberIgnoreCaseAndStatus(
                "RJ14XY5678", SessionStatus.ACTIVE)).thenReturn(Optional.empty());

        PlateTransferResponse response = parkingService.transferPlate(request(
                " rj14ab1234 ", " rj14xy5678 "));

        assertEquals("RJ14AB1234", response.oldPlateNumber());
        assertEquals("RJ14XY5678", response.newPlateNumber());
        assertEquals("RJ14XY5678", activeSession.getPlateNumber());
        assertEquals("C-01", response.spotNumber());
        assertEquals(checkInTime, response.checkInTime());
        assertEquals(SessionStatus.ACTIVE, response.status());
        assertEquals(spot, activeSession.getParkingSpot());
        assertEquals(checkInTime, activeSession.getCheckInTime());
        assertEquals(BigDecimal.valueOf(50), activeSession.getAppliedFirstHourRate());
        verify(parkingSessionRepository).save(activeSession);
        verifyNoInteractions(parkingSpotRepository);
        verifyNoInteractions(rateCardService);
    }

    @Test
    void rejectsMissingOldPlateSession() {
        when(parkingSessionRepository.findFirstByPlateNumberIgnoreCaseAndStatus(
                "MISSING", SessionStatus.ACTIVE)).thenReturn(Optional.empty());
        when(parkingSessionRepository.findByPlateNumberIgnoreCase("MISSING"))
                .thenReturn(List.of());

        assertThrows(NotFoundException.class,
                () -> parkingService.transferPlate(request("MISSING", "NEW123")));
        verify(parkingSessionRepository, never()).save(activeSession);
    }

    @Test
    void rejectsCompletedOldSession() {
        ParkingSession completedSession = new ParkingSession("RJ14AB1234", VehicleType.COMPACT,
                spot, checkInTime, SessionStatus.COMPLETED);
        when(parkingSessionRepository.findFirstByPlateNumberIgnoreCaseAndStatus(
                "RJ14AB1234", SessionStatus.ACTIVE)).thenReturn(Optional.empty());
        when(parkingSessionRepository.findByPlateNumberIgnoreCase("RJ14AB1234"))
                .thenReturn(List.of(completedSession));

        assertThrows(ConflictException.class,
                () -> parkingService.transferPlate(request("RJ14AB1234", "NEW123")));
    }

    @Test
    void rejectsDestinationWithActiveSession() {
        when(parkingSessionRepository.findFirstByPlateNumberIgnoreCaseAndStatus(
                "RJ14AB1234", SessionStatus.ACTIVE)).thenReturn(Optional.of(activeSession));
        when(parkingSessionRepository.findFirstByPlateNumberIgnoreCaseAndStatus(
                "RJ14XY5678", SessionStatus.ACTIVE))
                .thenReturn(Optional.of(new ParkingSession("RJ14XY5678", VehicleType.STANDARD,
                        new ParkingSpot("S-01", 1, SpotType.STANDARD), checkInTime,
                        SessionStatus.ACTIVE)));

        assertThrows(ConflictException.class,
                () -> parkingService.transferPlate(request("RJ14AB1234", "RJ14XY5678")));
        assertEquals("RJ14AB1234", activeSession.getPlateNumber());
        verify(parkingSessionRepository, never()).save(activeSession);
    }

    @Test
    void rejectsSameNormalizedPlate() {
        assertThrows(ConflictException.class,
                () -> parkingService.transferPlate(request(" rj14ab1234 ", "RJ14AB1234")));
        verifyNoInteractions(parkingSessionRepository);
    }

    @Test
    void normalCheckoutStillWorksAfterTransfer() {
        when(parkingSessionRepository.findFirstByPlateNumberIgnoreCaseAndStatus(
                "RJ14AB1234", SessionStatus.ACTIVE)).thenReturn(Optional.of(activeSession));
        when(parkingSessionRepository.findFirstByPlateNumberIgnoreCaseAndStatus(
                "RJ14XY5678", SessionStatus.ACTIVE)).thenReturn(Optional.empty());
        when(parkingSessionRepository.findById(1L)).thenReturn(Optional.of(activeSession));
        when(rateCardService.getActiveRate(SpotType.COMPACT)).thenReturn(new RateCard(
                SpotType.COMPACT, BigDecimal.valueOf(50), BigDecimal.valueOf(30), BigDecimal.valueOf(200)));

        parkingService.transferPlate(request("RJ14AB1234", "RJ14XY5678"));
        parkingService.checkOut(1L);

        assertEquals("RJ14XY5678", activeSession.getPlateNumber());
        assertEquals(SessionStatus.COMPLETED, activeSession.getStatus());
        assertEquals(spot, activeSession.getParkingSpot());
        assertEquals(false, spot.isOccupied());
    }

    private PlateTransferRequest request(String oldPlateNumber, String newPlateNumber) {
        PlateTransferRequest request = new PlateTransferRequest();
        request.setOldPlateNumber(oldPlateNumber);
        request.setNewPlateNumber(newPlateNumber);
        return request;
    }
}