package com.licenta.coreservice.sensor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record CreateSensorRequest(
        @NotBlank String name,
        @NotBlank String type,
        @NotBlank String observed,
        @NotBlank String unit,
        String symbol,
        @JsonProperty("external_id") String externalId) {
}
