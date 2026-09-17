package com.parkease.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "rate_cards")
public class RateCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SpotType spotType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal firstHourRate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal additionalHourRate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal dailyCap;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RateCard() {
    }

    public RateCard(SpotType spotType, BigDecimal firstHourRate,
                    BigDecimal additionalHourRate, BigDecimal dailyCap) {
        this.spotType = spotType;
        this.firstHourRate = firstHourRate;
        this.additionalHourRate = additionalHourRate;
        this.dailyCap = dailyCap;
        this.active = true;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public SpotType getSpotType() {
        return spotType;
    }

    public BigDecimal getFirstHourRate() {
        return firstHourRate;
    }

    public BigDecimal getAdditionalHourRate() {
        return additionalHourRate;
    }

    public BigDecimal getDailyCap() {
        return dailyCap;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}