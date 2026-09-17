package com.parkease.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.parkease.dto.RateCardImportResponse;
import com.parkease.service.RateCardService;

@RestController
@RequestMapping("/api/rates")
public class RateCardController {

    private final RateCardService rateCardService;

    public RateCardController(RateCardService rateCardService) {
        this.rateCardService = rateCardService;
    }

    @PostMapping(value = "/import", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<RateCardImportResponse> importRates(@RequestBody String rawRateCard) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rateCardService.importRates(rawRateCard));
    }
}