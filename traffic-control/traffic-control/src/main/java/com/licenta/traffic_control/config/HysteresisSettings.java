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
    /** Fixed percentage applied each tick while out of band. */
    private double step;
    /** Proportional kick per unit of overshoot past the crossed edge. */
    private double gain;
    /** Lower clamp on the emitted percentage. */
    private double outputMin;
    /** Upper clamp on the emitted percentage. */
    private double outputMax;
}
