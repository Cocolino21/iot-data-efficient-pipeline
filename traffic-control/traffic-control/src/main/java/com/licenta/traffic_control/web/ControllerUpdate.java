package com.licenta.traffic_control.web;

import com.licenta.traffic_control.controller.ControllerMode;

/**
 * Partial-update payload for {@code PUT /api/controller}. All fields are nullable
 * so the UI can save any subset (mode switch, dead zone, or poll interval) without
 * clobbering the others — and a sent {@code 0} dead zone is distinguishable from
 * "omitted".
 */
public record ControllerUpdate(ControllerMode mode, Double deadZone, Long pollIntervalMs) {
}
