package com.licenta.traffic_control.service;

import com.licenta.traffic_control.domain.enums.ControllerMode;

public interface LagController {

    ControllerMode mode();

    double compute(long currentLag);

    default void reset() {
    }
}
