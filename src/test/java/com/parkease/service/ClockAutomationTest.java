package com.parkease.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.parkease.dto.ClockResponse;
import com.parkease.entity.ParkingSession;
import com.parkease.entity.ParkingSpot;
import com.parkease.entity.RateCard;
import com.parkease.entity.SessionStatus;
import com.parkease.entity.SpotType;
import com.parkease.entity.VehicleType;
import com.parkease.repository.ParkingSessionRepository;
import com.parkease.repository.ParkingSpotRepository;

@ExtendWith(MockitoExtension.class)
class ClockAutomationTest {

    private static final LocalDateTime CURRENT_TIME = LocalDateTime.of(2026, 9, 17, 12, 0);

    @Mock
    private ParkingSessionRepository parkingSessionRepository;

    @Mock
    private ParkingSpotRepository parkingSpotRepository;

    @Mock
    private RateCardService rateCardService;

    private ParkingService parkingService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-17T12:00:00Z"), ZoneOffset.UTC);
        parkingService = new ParkingService(parkingSessionRepository, parkingSpotRepository,
                new FeeCalculatorService(), rateCardService, clock);
    }

    @Test
    void leavesSessionsUnderOrExactlyTwentyFourHoursActive() {
        ParkingSession under = sessionAt(CURRENT_TIME.minusHours(23));
        ParkingSession exact = sessionAt(CURRENT_TIME.minusHours(24));
        when(parkingSessionRepository.findByStatus(SessionStatus.ACTIVE))
                .thenReturn(List.of(under, exact));

        ClockResponse response = parkingService.autoCloseOverdueSessions();

        assertEquals(0, response.closedSessions());
        assertEquals(0, response.spotsFreed());
        assertEquals(SessionStatus.ACTIVE, under.getStatus());
        assertEquals(SessionStatus.ACTIVE, exact.getStatus());
    }

    @Test
    void closesOverdueSessionWithImportedRateAndFreesSpot() {
        ParkingSession overdue = sessionAt(CURRENT_TIME.minusHours(25));
        RateCard rate = new RateCard(SpotType.COMPACT,
            BigDecimal.valueOf(50), BigDecimal.valueOf(30), BigDecimal.valueOf(1000));
        when(parkingSessionRepository.findByStatus(SessionStatus.ACTIVE))
                .thenReturn(List.of(overdue));
        when(rateCardService.getActiveRate(SpotType.COMPACT)).thenReturn(rate);

        ClockResponse response = parkingService.autoCloseOverdueSessions();

        assertEquals(1, response.closedSessions());
        assertEquals(1, response.spotsFreed());
        assertEquals(SessionStatus.COMPLETED, overdue.getStatus());
        assertEquals(CURRENT_TIME, overdue.getCheckOutTime());
        assertEquals(BigDecimal.valueOf(770), overdue.getFee());
        assertEquals(BigDecimal.valueOf(50), overdue.getAppliedFirstHourRate());
        assertEquals(false, overdue.getParkingSpot().isOccupied());
        verify(parkingSessionRepository).save(overdue);
        verify(parkingSpotRepository).save(overdue.getParkingSpot());
    }

    @Test
    void closesAllOverdueActiveSessionsAndDoesNotProcessCompletedSessions() {
        ParkingSession first = sessionAt(CURRENT_TIME.minusHours(25));
        ParkingSession second = sessionAt(CURRENT_TIME.minusHours(48));
        when(parkingSessionRepository.findByStatus(SessionStatus.ACTIVE))
                .thenReturn(List.of(first, second));
        when(rateCardService.getActiveRate(SpotType.COMPACT)).thenReturn(new RateCard(
                SpotType.COMPACT, BigDecimal.valueOf(50), BigDecimal.valueOf(30), BigDecimal.valueOf(200)));

        ClockResponse response = parkingService.autoCloseOverdueSessions();

        assertEquals(2, response.closedSessions());
        assertEquals(SessionStatus.COMPLETED, first.getStatus());
        assertEquals(SessionStatus.COMPLETED, second.getStatus());

        when(parkingSessionRepository.findByStatus(SessionStatus.ACTIVE)).thenReturn(List.of());
        ClockResponse secondRun = parkingService.autoCloseOverdueSessions();
        assertEquals(0, secondRun.closedSessions());
    }

    @Test
    void reportsZeroWhenThereAreNoOverdueSessions() {
        when(parkingSessionRepository.findByStatus(SessionStatus.ACTIVE)).thenReturn(List.of());

        ClockResponse response = parkingService.autoCloseOverdueSessions();

        assertEquals(0, response.closedSessions());
        assertEquals(0, response.spotsFreed());
        assertEquals(0, response.skippedSessions());
    }

    private ParkingSession sessionAt(LocalDateTime checkInTime) {
        ParkingSpot spot = new ParkingSpot("C-" + checkInTime.getHour(), 1, SpotType.COMPACT);
        spot.setOccupied(true);
        return new ParkingSession("RJ14AB1234", VehicleType.COMPACT, spot,
                checkInTime, SessionStatus.ACTIVE);
    }
}