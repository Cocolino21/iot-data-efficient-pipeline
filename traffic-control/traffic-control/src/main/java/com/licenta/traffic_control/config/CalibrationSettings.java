package com.licenta.traffic_control.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "calibration")
public class CalibrationSettings {
    private int maxConcurrent = 5;
    private int collectionTtlSeconds = 86400;
    private int baselineDays = 5;
    private long pollIntervalMs = 60000;
}
