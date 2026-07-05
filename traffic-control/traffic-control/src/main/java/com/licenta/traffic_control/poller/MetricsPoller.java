package com.licenta.traffic_control.poller;

import com.licenta.traffic_control.actuator.MqttActuator;
import com.licenta.traffic_control.config.ControllerSettings;
import com.licenta.traffic_control.controller.ControllerRegistry;
import com.licenta.traffic_control.controller.LagController;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsPoller {

    private final PrometheusClient prometheusClient;
    private final ControllerRegistry registry;
    private final MqttActuator mqttActuator;
    private final ControllerSettings controllerSettings;
    private final MeterRegistry meterRegistry;

    // Magnitude of the last adjustment (a gauge — holds its value between polls).
    private final AtomicReference<Double> lastAdjustment = new AtomicReference<>(0.0);
    // Monotonic count of actual broadcasts: lets Grafana show discrete send
    // events via increase()/rate() instead of a held last-value line.
    private Counter broadcastCounter;

    @PostConstruct
    void registerMetrics() {
        Gauge.builder("traffic_control_adjustment", lastAdjustment, AtomicReference::get)
                .description("Last PIP percentage adjustment computed by the active controller")
                .register(meterRegistry);
        broadcastCounter = Counter.builder("traffic_control_broadcasts_total")
                .description("Total adjustment messages broadcast to devices (i.e. cleared the dead zone)")
                .register(meterRegistry);
    }

    // Scheduled by ControllerSchedulingConfig with a dynamic interval so
    // controller.pollIntervalMs can be changed at runtime from the UI.
    public void poll() {
        try {
            LagController active = registry.active();
            if (active == null) {
                lastAdjustment.set(0.0); // mode = NONE: control loop disabled
                return;
            }

            long lag = prometheusClient.getConsumerLag();
            double adjustment = active.compute(lag);
            boolean broadcast = Math.abs(adjustment) > controllerSettings.getDeadZone();

            log.info("mode={}, lag={}, adjustment={}", active.mode(), lag, String.format("%.2f", adjustment));

            lastAdjustment.set(adjustment);

            if (broadcast) {
                broadcastCounter.increment();
                mqttActuator.publishPipPercentage(adjustment);
            }
        } catch (Exception e) {
            log.error("Failed to poll Prometheus: {}", e.getMessage());
        }
    }
}
