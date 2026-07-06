package com.licenta.traffic_control.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gap reconstruction for PIP-filtered streams. Energy streams (identified by
 * {@code datastream.observation_type = 'energy'}, declared by the device and
 * written at registration) use the device baseline's hour-of-day shape;
 * everything else — and energy streams whose current data drifts too far from
 * their baseline to trust it — falls back to linear interpolation.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "reconstruction")
public class ReconstructionSettings {
    /** A gap longer than this many seconds gets reconstructed points. */
    private int gapSeconds = 5;
    /** Cap on synthetic points generated per gap. */
    private int maxPointsPerGap = 60;
    /**
     * Baseline trust gate: if the stream's relative drift over the last 24 h
     * exceeds this, reconstruction falls back to interpolation until a
     * calibration cycle rebuilds the baseline.
     */
    private double maxDrift = 0.15;
}
