package com.licenta.traffic_control.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "reconstruction")
public class ReconstructionSettings {
    private int gapSeconds = 5;
    private int maxPointsPerGap = 60;
    private double samplesPerHour = 3600;
}
