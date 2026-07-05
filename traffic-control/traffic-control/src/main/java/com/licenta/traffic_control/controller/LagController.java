package com.licenta.traffic_control.controller;

public interface LagController {

    ControllerMode mode();

    double compute(long currentLag);

    default void reset() {
    }
}
