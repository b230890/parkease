package com.parkease.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.parkease.entity.ParkingSpot;
import com.parkease.entity.SpotType;

public interface ParkingSpotRepository extends JpaRepository<ParkingSpot, Long> {

    List<ParkingSpot> findByOccupiedFalseAndType(SpotType type);
}
