package com.licenta.traffic_control.controller;

/**
 * Which threshold-adjustment strategy {@code MetricsPoller} is currently driving.
 * Exactly one is active at a time; {@code NONE} disables broadcasting entirely.
 */
public enum ControllerMode {
    NONE,
    PID,
    HYSTERESIS
}
