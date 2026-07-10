package com.licenta.traffic_control.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "hysteresis")
public class HysteresisSettings {
    private long upperLag;
    private long lowerLag;
    private double step;
    private double gain;
    private double relaxStep;
    private double outputMin;
    private double outputMax;
}
