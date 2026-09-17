package com.parkease.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.parkease.entity.RateCard;
import com.parkease.entity.SpotType;

class FeeCalculatorServiceTest {

    private final FeeCalculatorService feeCalculatorService = new FeeCalculatorService();
    private final RateCard rateCard = new RateCard(
            SpotType.COMPACT,
            BigDecimal.valueOf(50),
            BigDecimal.valueOf(30),
            BigDecimal.valueOf(200));

    @Test
    void calculatesImportedRateForPartialHour() {
        LocalDateTime checkIn = LocalDateTime.of(2026, 9, 17, 10, 0);

        assertEquals(BigDecimal.valueOf(80), feeCalculatorService.calculateFee(
                checkIn, checkIn.plusMinutes(61), rateCard));
    }

    @Test
    void appliesDailyCap() {
        LocalDateTime checkIn = LocalDateTime.of(2026, 9, 17, 10, 0);

        assertEquals(BigDecimal.valueOf(200), feeCalculatorService.calculateFee(
                checkIn, checkIn.plusHours(10), rateCard));
    }
}