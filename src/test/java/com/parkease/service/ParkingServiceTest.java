package com.parkease.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.parkease.entity.ParkingSession;
import com.parkease.entity.ParkingSpot;
import com.parkease.entity.RateCard;
import com.parkease.entity.SessionStatus;
import com.parkease.entity.SpotType;
import com.parkease.entity.VehicleType;
import com.parkease.repository.ParkingSessionRepository;
import com.parkease.repository.ParkingSpotRepository;

@ExtendWith(MockitoExtension.class)
class ParkingServiceTest {

    @Mock
    private ParkingSessionRepository parkingSessionRepository;

    @Mock
    private ParkingSpotRepository parkingSpotRepository;

    @Mock
    private RateCardService rateCardService;

    @Test
    void completedFeeRemainsStoredAfterRateChanges() {
        ParkingSpot spot = new ParkingSpot("C-01", 1, SpotType.COMPACT);
        ParkingSession session = new ParkingSession("RJ14AB1234", VehicleType.COMPACT, spot,
                LocalDateTime.now().minusMinutes(61), SessionStatus.ACTIVE);
        RateCard originalRate = new RateCard(SpotType.COMPACT,
                BigDecimal.valueOf(50), BigDecimal.valueOf(30), BigDecimal.valueOf(200));
        when(parkingSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(rateCardService.getActiveRate(SpotType.COMPACT)).thenReturn(originalRate);

        ParkingService parkingService = new ParkingService(parkingSessionRepository,
                parkingSpotRepository, new FeeCalculatorService(), rateCardService);
        parkingService.checkOut(1L);
        BigDecimal completedFee = session.getFee();

        assertEquals(BigDecimal.valueOf(80), completedFee);
        assertEquals(BigDecimal.valueOf(80), session.getFee());
        assertEquals(SessionStatus.COMPLETED, session.getStatus());
                assertEquals(BigDecimal.valueOf(50), session.getAppliedFirstHourRate());
                assertEquals(BigDecimal.valueOf(30), session.getAppliedAdditionalHourRate());
                assertEquals(BigDecimal.valueOf(200), session.getAppliedDailyCap());
    }
}