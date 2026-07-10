package com.licenta.coreservice.dto;

import java.util.List;
import java.util.UUID;

public record DeviceDto(
        UUID id,
        String name,
        String description,
        Double latitude,
        Double longitude,
        String status,
        Long last_seen_at,
        List<SensorDto> sensors) {
}
