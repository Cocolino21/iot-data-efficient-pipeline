package com.licenta.coreservice.dto;

import java.util.UUID;

public record SensorDto(
        UUID id,
        String name,
        String type,
        String observed,
        String unit,
        String symbol,
        boolean is_active,
        String external_id) {
}
