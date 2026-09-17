package com.parkease.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.parkease.dto.RateCardImportResponse;
import com.parkease.entity.RateCard;
import com.parkease.entity.SpotType;
import com.parkease.exception.BadRequestException;
import com.parkease.repository.RateCardRepository;

@ExtendWith(MockitoExtension.class)
class RateCardServiceTest {

    @Mock
    private RateCardRepository rateCardRepository;

    @Test
    void importsAllSupportedTypesAndNormalizesWhitespaceAndCase() {
        when(rateCardRepository.findBySpotTypeAndActiveTrue(any())).thenReturn(List.of());
        when(rateCardRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        RateCardService rateCardService = new RateCardService(rateCardRepository);

        RateCardImportResponse response = rateCardService.importRates("""
                spotType,firstHourRate,additionalHourRate,dailyCap
                 compact , 50.00 , 30 , 200
                STANDARD,60,35,250
                ev,70,40,300
                # ignored comment
                """);

        assertEquals(3, response.importedCount());
        assertEquals(List.of(SpotType.COMPACT, SpotType.STANDARD, SpotType.EV),
                response.rates().stream().map(RateCardImportResponse.RateCardResponse::spotType).toList());
    }

    @Test
    void rejectsMalformedAndInvalidRecords() {
        RateCardService rateCardService = new RateCardService(rateCardRepository);

        assertThrows(BadRequestException.class,
                () -> rateCardService.importRates("COMPACT,not-money,30,200"));
        assertThrows(BadRequestException.class,
                () -> rateCardService.importRates("BIKE,50,30,200"));
        assertThrows(BadRequestException.class,
                () -> rateCardService.importRates("COMPACT,50,30"));
    }

    @Test
    void rejectsDuplicateTypesInOneImport() {
        RateCardService rateCardService = new RateCardService(rateCardRepository);

        assertThrows(BadRequestException.class, () -> rateCardService.importRates("""
                COMPACT,50,30,200
                compact,55,35,220
                """));
    }
}