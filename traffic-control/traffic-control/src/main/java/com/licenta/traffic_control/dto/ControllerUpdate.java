package com.licenta.traffic_control.dto;

import com.licenta.traffic_control.domain.enums.ControllerMode;

public record ControllerUpdate(ControllerMode mode, Double deadZone, Long pollIntervalMs) {
}
