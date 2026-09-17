package com.parkease.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PlateTransferRequest {

    @NotBlank
    @Size(max = 20)
    private String oldPlateNumber;

    @NotBlank
    @Size(max = 20)
    private String newPlateNumber;

    public String getOldPlateNumber() {
        return oldPlateNumber;
    }

    public void setOldPlateNumber(String oldPlateNumber) {
        this.oldPlateNumber = oldPlateNumber;
    }

    public String getNewPlateNumber() {
        return newPlateNumber;
    }

    public void setNewPlateNumber(String newPlateNumber) {
        this.newPlateNumber = newPlateNumber;
    }
}