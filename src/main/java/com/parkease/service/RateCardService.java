package com.parkease.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.parkease.dto.RateCardImportResponse;
import com.parkease.dto.RateCardImportResponse.RateCardResponse;
import com.parkease.entity.RateCard;
import com.parkease.entity.SpotType;
import com.parkease.exception.BadRequestException;
import com.parkease.exception.NotFoundException;
import com.parkease.repository.RateCardRepository;

@Service
public class RateCardService {

    private static final Set<SpotType> SUPPORTED_TYPES = EnumSet.allOf(SpotType.class);

    private final RateCardRepository rateCardRepository;

    public RateCardService(RateCardRepository rateCardRepository) {
        this.rateCardRepository = rateCardRepository;
    }

    @Transactional
    public RateCardImportResponse importRates(String rawRateCard) {
        if (rawRateCard == null || rawRateCard.isBlank()) {
            throw new BadRequestException("Rate card import is empty");
        }

        List<RateCard> cleanedRates = new ArrayList<>();
        Set<SpotType> importedTypes = new HashSet<>();
        String[] lines = rawRateCard.split("\\R");
        for (int lineNumber = 0; lineNumber < lines.length; lineNumber++) {
            String line = lines[lineNumber].trim();
            if (line.isEmpty() || line.startsWith("#") || isHeader(line)) {
                continue;
            }

            String[] fields = line.split(",", -1);
            if (fields.length != 4) {
                throw new BadRequestException("Malformed rate record on line " + (lineNumber + 1));
            }

            SpotType spotType = parseSpotType(fields[0], lineNumber + 1);
            if (!importedTypes.add(spotType)) {
                throw new BadRequestException("Duplicate active rate for " + spotType);
            }

            BigDecimal firstHourRate = parsePositiveMoney(fields[1], "first-hour rate", lineNumber + 1);
            BigDecimal additionalHourRate = parsePositiveMoney(
                    fields[2], "additional-hour rate", lineNumber + 1);
            BigDecimal dailyCap = parsePositiveMoney(fields[3], "daily cap", lineNumber + 1);
            if (dailyCap.compareTo(firstHourRate) < 0) {
                throw new BadRequestException("Daily cap must be at least the first-hour rate on line "
                        + (lineNumber + 1));
            }
            cleanedRates.add(new RateCard(spotType, firstHourRate, additionalHourRate, dailyCap));
        }

        if (cleanedRates.isEmpty()) {
            throw new BadRequestException("Rate card contains no valid records");
        }

        for (RateCard rate : cleanedRates) {
            rateCardRepository.findBySpotTypeAndActiveTrue(rate.getSpotType())
                    .forEach(existingRate -> existingRate.setActive(false));
        }
        List<RateCard> savedRates = rateCardRepository.saveAll(cleanedRates);
        return new RateCardImportResponse(savedRates.size(), savedRates.stream()
                .map(rate -> new RateCardResponse(rate.getSpotType(), rate.getFirstHourRate(),
                        rate.getAdditionalHourRate(), rate.getDailyCap()))
                .toList());
    }

    @Transactional(readOnly = true)
    public RateCard getActiveRate(SpotType spotType) {
        List<RateCard> activeRates = rateCardRepository.findBySpotTypeAndActiveTrue(spotType);
        if (activeRates.isEmpty()) {
            throw new NotFoundException("No active rate exists for spot type " + spotType);
        }
        if (activeRates.size() > 1) {
            throw new BadRequestException("Multiple active rates exist for spot type " + spotType);
        }
        return activeRates.getFirst();
    }

    private SpotType parseSpotType(String rawType, int lineNumber) {
        String normalizedType = rawType.trim().toUpperCase(Locale.ROOT);
        try {
            SpotType spotType = SpotType.valueOf(normalizedType);
            if (!SUPPORTED_TYPES.contains(spotType)) {
                throw new IllegalArgumentException();
            }
            return spotType;
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Unsupported spot type on line " + lineNumber + ": " + rawType);
        }
    }

    private BigDecimal parsePositiveMoney(String rawValue, String fieldName, int lineNumber) {
        try {
            BigDecimal value = new BigDecimal(rawValue.trim());
            if (value.signum() <= 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new BadRequestException("Invalid " + fieldName + " on line " + lineNumber);
        }
    }

    private boolean isHeader(String line) {
        String normalized = line.toLowerCase(Locale.ROOT);
        return normalized.startsWith("spot_type,")
                || normalized.startsWith("spottype,")
                || normalized.startsWith("type,");
    }
}