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
    // Relative drift: mean |actual - expected| / mean expected level.
    // 0.15 = flag when the signal is ~15% off its own baseline.
    private double driftThreshold = 0.15;
    // Minimum joined baseline-hours in the 3-day window before drift is trusted.
    private int minHours = 24;
}
