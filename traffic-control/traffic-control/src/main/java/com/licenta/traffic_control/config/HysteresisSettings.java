package com.licenta.traffic_control.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tuning for the two-level hysteresis controller. Activation is governed by
 * {@code controller.mode}, so there is no {@code enabled} flag here.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "hysteresis")
public class HysteresisSettings {
    /** Lag above this edge triggers a positive (shed) adjustment. */
    private long upperLag;
    /** Lag below this edge triggers a negative (relax) adjustment. */
    private long lowerLag;
    /** Fixed shed percentage applied each tick while above the upper edge. */
    private double step;
    /** Extra shed percentage per 100% relative overshoot past the upper edge. */
    private double gain;
    /**
     * Flat relax percentage applied each tick while below the lower edge.
     * Deliberately small relative to {@code step}: the actuator is cumulative,
     * so a gentle constant decay lets traffic return without re-overshooting
     * the upper edge (shed fast, relax slow).
     */
    private double relaxStep;
    /** Lower clamp on the emitted percentage. */
    private double outputMin;
    /** Upper clamp on the emitted percentage. */
    private double outputMax;
}
