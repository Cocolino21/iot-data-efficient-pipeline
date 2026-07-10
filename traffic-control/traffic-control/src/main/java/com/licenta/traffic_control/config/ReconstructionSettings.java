package com.licenta.traffic_control.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gap handling for PIP-filtered streams: raw-level gaps get linear
 * interpolation; the baseline enters only at the hourly aggregate level as a
 * coverage-weighted adjustment.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "reconstruction")
public class ReconstructionSettings {
    /** A gap longer than this many seconds gets interpolated points. */
    private int gapSeconds = 5;
    /** Cap on synthetic points generated per gap. */
    private int maxPointsPerGap = 60;
    /**
     * Full-coverage sample count for one hourly bucket (1 Hz sensors -> 3600).
     * Coverage = sample_count / this, capped at 1; it weights measured average
     * vs baseline expectation in the adjusted hourly aggregate.
     */
    private double samplesPerHour = 3600;
}
