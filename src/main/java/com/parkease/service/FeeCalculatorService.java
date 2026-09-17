package com.parkease.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.parkease.exception.BadRequestException;

@Service
public class FeeCalculatorService {

    private static final long SECONDS_PER_HOUR = 60 * 60;
    private static final BigDecimal FIRST_HOUR_FEE = BigDecimal.valueOf(50);
    private static final BigDecimal ADDITIONAL_HOUR_FEE = BigDecimal.valueOf(30);
    private static final BigDecimal MAXIMUM_DAILY_FEE = BigDecimal.valueOf(200);

    public BigDecimal calculateFee(LocalDateTime checkInTime, LocalDateTime checkOutTime) {
        if (checkInTime == null || checkOutTime == null || checkOutTime.isBefore(checkInTime)) {
            throw new BadRequestException("Checkout time must not be before check-in time");
        }

        Duration duration = Duration.between(checkInTime, checkOutTime);
        long elapsedSeconds = duration.getSeconds();
        long chargedHours = elapsedSeconds / SECONDS_PER_HOUR;
        if (elapsedSeconds % SECONDS_PER_HOUR != 0 || duration.getNano() != 0) {
            chargedHours++;
        }

        if (chargedHours <= 1) {
            return FIRST_HOUR_FEE;
        }

        BigDecimal fee = FIRST_HOUR_FEE.add(
                ADDITIONAL_HOUR_FEE.multiply(BigDecimal.valueOf(chargedHours - 1)));
        return fee.min(MAXIMUM_DAILY_FEE);
    }
}