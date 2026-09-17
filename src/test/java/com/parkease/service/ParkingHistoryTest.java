package com.parkease.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.parkease.dto.ParkingHistoryResponse;
import com.parkease.entity.ParkingSession;
import com.parkease.entity.ParkingSpot;
import com.parkease.entity.SessionStatus;
import com.parkease.entity.SpotType;
import com.parkease.entity.VehicleType;
import com.parkease.exception.BadRequestException;
import com.parkease.repository.ParkingSessionRepository;
import com.parkease.repository.ParkingSpotRepository;

@ExtendWith(MockitoExtension.class)
class ParkingHistoryTest {

    @Mock
    private ParkingSessionRepository parkingSessionRepository;

    @Mock
    private ParkingSpotRepository parkingSpotRepository;

    @Mock
    private RateCardService rateCardService;

    private ParkingService parkingService;

    @BeforeEach
    void setUp() {
        parkingService = new ParkingService(parkingSessionRepository, parkingSpotRepository,
                new FeeCalculatorService(), rateCardService);
    }

    @Test
    void returnsCompletedSessionsWithPaginationMetadata() {
        Pageable pageable = PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "checkInTime"));
        ParkingSession completed = session(SessionStatus.COMPLETED, "RJ14AB1234");
        when(parkingSessionRepository.findByStatus(SessionStatus.COMPLETED, pageable))
                .thenReturn(new PageImpl<>(List.of(completed), pageable, 3));

        ParkingHistoryResponse response = parkingService.findHistory(pageable);

        assertEquals(1, response.content().size());
        assertEquals(SessionStatus.COMPLETED, response.content().getFirst().status());
        assertEquals(0, response.page());
        assertEquals(1, response.size());
        assertEquals(3, response.totalElements());
        assertEquals(3, response.totalPages());
        verify(parkingSessionRepository).findByStatus(SessionStatus.COMPLETED, pageable);
    }

    @Test
    void activeSessionsAreExcludedByCompletedRepositoryQuery() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "checkInTime"));
        when(parkingSessionRepository.findByStatus(SessionStatus.COMPLETED, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        ParkingHistoryResponse response = parkingService.findHistory(pageable);

        assertEquals(0, response.content().size());
        verify(parkingSessionRepository).findByStatus(SessionStatus.COMPLETED, pageable);
    }

    @Test
    void supportsCheckInTimeAndFeeSorting() {
        Pageable checkInSort = PageRequest.of(1, 10,
                Sort.by(Sort.Direction.DESC, "checkInTime"));
        Pageable feeSort = PageRequest.of(0, 10,
                Sort.by(Sort.Direction.ASC, "fee"));
        when(parkingSessionRepository.findByStatus(SessionStatus.COMPLETED, checkInSort))
                .thenReturn(new PageImpl<>(List.of(), checkInSort, 0));
        when(parkingSessionRepository.findByStatus(SessionStatus.COMPLETED, feeSort))
                .thenReturn(new PageImpl<>(List.of(), feeSort, 0));

        parkingService.findHistory(checkInSort);
        parkingService.findHistory(feeSort);

        verify(parkingSessionRepository).findByStatus(SessionStatus.COMPLETED, checkInSort);
        verify(parkingSessionRepository).findByStatus(SessionStatus.COMPLETED, feeSort);
    }

    @Test
    void rejectsUnsafePageSizeAndSortField() {
        assertThrows(BadRequestException.class, () -> parkingService.findHistory(
                PageRequest.of(0, 101, Sort.by("checkInTime"))));
        assertThrows(BadRequestException.class, () -> parkingService.findHistory(
                PageRequest.of(0, 10, Sort.by("status"))));
    }

    private ParkingSession session(SessionStatus status, String plateNumber) {
        ParkingSpot spot = new ParkingSpot("C-01", 1, SpotType.COMPACT);
        ParkingSession session = new ParkingSession(plateNumber, VehicleType.COMPACT, spot,
                LocalDateTime.of(2026, 9, 17, 10, 0), status);
        session.setCheckOutTime(LocalDateTime.of(2026, 9, 17, 11, 0));
        session.setFee(BigDecimal.valueOf(50));
        return session;
    }
}