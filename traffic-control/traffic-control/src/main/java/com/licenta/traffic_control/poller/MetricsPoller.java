package com.licenta.traffic_control.poller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsPoller {

    private final PrometheusClient prometheusClient;

    @Scheduled(fixedDelayString = "${controller.poll-interval-ms}")
    public void poll() {
        try {
            long lag = prometheusClient.getConsumerLag();
            log.info("kafka consumer lag: {}", lag);
        } catch (Exception e) {
            log.error("Failed to poll Prometheus: {}", e.getMessage());
        }
    }
}
