package com.licenta.coreservice.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDeviceRequest(
        @NotBlank String name,
        String description,
        Double latitude,
        Double longitude) {
}
