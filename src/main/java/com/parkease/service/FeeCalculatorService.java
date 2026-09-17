package com.parkease.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.parkease.entity.RateCard;
import com.parkease.exception.BadRequestException;

@Service
public class FeeCalculatorService {

    private static final long SECONDS_PER_HOUR = 60 * 60;
    public BigDecimal calculateFee(LocalDateTime checkInTime, LocalDateTime checkOutTime,
                                   RateCard rateCard) {
        if (checkInTime == null || checkOutTime == null || checkOutTime.isBefore(checkInTime)) {
            throw new BadRequestException("Checkout time must not be before check-in time");
        }
        if (rateCard == null) {
            throw new BadRequestException("An applicable rate is required");
        }

        Duration duration = Duration.between(checkInTime, checkOutTime);
        long elapsedSeconds = duration.getSeconds();
        long chargedHours = elapsedSeconds / SECONDS_PER_HOUR;
        if (elapsedSeconds % SECONDS_PER_HOUR != 0 || duration.getNano() != 0) {
            chargedHours++;
        }

        if (chargedHours <= 1) {
            return rateCard.getFirstHourRate();
        }

        BigDecimal fee = rateCard.getFirstHourRate().add(
                rateCard.getAdditionalHourRate().multiply(BigDecimal.valueOf(chargedHours - 1)));
        return fee.min(rateCard.getDailyCap());
    }
}