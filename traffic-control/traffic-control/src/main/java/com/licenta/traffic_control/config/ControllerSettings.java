package com.licenta.traffic_control.config;

import com.licenta.traffic_control.domain.enums.ControllerMode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "controller")
public class ControllerSettings {
    private ControllerMode mode = ControllerMode.NONE;
    private double deadZone = 1.0;
    private long pollIntervalMs = 10000;
}
