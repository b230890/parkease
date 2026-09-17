package com.parkease.dto;

import java.math.BigDecimal;
import java.util.List;

import com.parkease.entity.SpotType;

public record RateCardImportResponse(int importedCount, List<RateCardResponse> rates) {

    public record RateCardResponse(
            SpotType spotType,
            BigDecimal firstHourRate,
            BigDecimal additionalHourRate,
            BigDecimal dailyCap) {
    }
}