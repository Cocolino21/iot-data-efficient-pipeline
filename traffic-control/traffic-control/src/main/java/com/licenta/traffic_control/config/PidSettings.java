package com.licenta.traffic_control.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "pid")
public class PidSettings {
    private long targetLag;
    private double kp;
    private double ki;
    private double kd;
    private double integralMax;
    private double outputMin;
    private double outputMax;
}
