package com.parkease.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.parkease.entity.RateCard;
import com.parkease.entity.SpotType;

public interface RateCardRepository extends JpaRepository<RateCard, Long> {

    List<RateCard> findBySpotTypeAndActiveTrue(SpotType spotType);

    List<RateCard> findByActiveTrue();
}