package com.parkease.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.parkease.dto.AvailabilitySummaryResponse;
import com.parkease.dto.ParkingSpotResponse;
import com.parkease.dto.SpotTypeAvailabilityResponse;
import com.parkease.entity.ParkingSpot;
import com.parkease.entity.SpotType;
import com.parkease.exception.BadRequestException;
import com.parkease.repository.ParkingSpotRepository;

@Service
public class ParkingSpotService {

    private final ParkingSpotRepository parkingSpotRepository;

    public ParkingSpotService(ParkingSpotRepository parkingSpotRepository) {
        this.parkingSpotRepository = parkingSpotRepository;
    }

    @Transactional(readOnly = true)
    public List<ParkingSpotResponse> findAll() {
        return parkingSpotRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AvailabilitySummaryResponse getSummary() {
        long totalSpots = parkingSpotRepository.count();
        long occupiedSpots = parkingSpotRepository.countByOccupiedTrue();
        return new AvailabilitySummaryResponse(
                totalSpots,
                occupiedSpots,
                totalSpots - occupiedSpots,
                countByType(SpotType.COMPACT),
                availableByType(SpotType.COMPACT),
                countByType(SpotType.STANDARD),
                availableByType(SpotType.STANDARD),
                countByType(SpotType.EV),
                availableByType(SpotType.EV));
    }

    @Transactional(readOnly = true)
    public SpotTypeAvailabilityResponse getAvailabilityByType(String type) {
        SpotType spotType = parseType(type);
        long total = countByType(spotType);
        long occupied = parkingSpotRepository.countByTypeAndOccupiedTrue(spotType);
        return new SpotTypeAvailabilityResponse(spotType, total, occupied, total - occupied);
    }

    private long countByType(SpotType type) {
        return parkingSpotRepository.countByType(type);
    }

    private long availableByType(SpotType type) {
        return countByType(type) - parkingSpotRepository.countByTypeAndOccupiedTrue(type);
    }

    private SpotType parseType(String type) {
        if (type == null || type.isBlank()) {
            throw new BadRequestException("Spot type is required");
        }
        try {
            return SpotType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Invalid spot type: " + type);
        }
    }

    private ParkingSpotResponse toResponse(ParkingSpot parkingSpot) {
        return new ParkingSpotResponse(
                parkingSpot.getId(),
                parkingSpot.getSpotNumber(),
                parkingSpot.getFloor(),
                parkingSpot.getType(),
                parkingSpot.isOccupied());
    }
}