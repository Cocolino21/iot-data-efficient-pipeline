package com.licenta.traffic_control.config;

import com.licenta.traffic_control.controller.ControllerMode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "controller")
public class ControllerSettings {
    private ControllerMode mode = ControllerMode.NONE;
    private double deadZone = 1.0;
    /** How often the control loop polls lag and (re)broadcasts; adjustable at runtime. */
    private long pollIntervalMs = 10000;
}
