package com.licenta.traffic_control.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "emqx")
public class EmqxTuningSettings {
    private boolean enabled = false;

    private String apiUrl = "http://localhost:18083";
    private String apiUser = "admin";
    private String apiPassword = "public";
    private String actionId = "kafka_producer:dt_to_kafka";

    private double dropRateThreshold = 1.0;
    private int cooldownPolls = 3;

    // defaults (what EMQX normally runs with)
    private String defaultMaxLingerTime = "20ms";
    private String defaultMaxLingerBytes = "1MB";
    private String defaultMaxBatchBytes = "1MB";
    private int defaultMaxInflight = 32;

    // upper limits (pushed when drops detected)
    private String upperMaxLingerTime = "50ms";
    private String upperMaxLingerBytes = "4MB";
    private String upperMaxBatchBytes = "4MB";
    private int upperMaxInflight = 128;
}
