package com.licenta.traffic_control.config;

import com.licenta.traffic_control.poller.MetricsPoller;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Instant;

/**
 * Schedules the control loop with a dynamic fixed-delay trigger that re-reads
 * {@link ControllerSettings#getPollIntervalMs()} each cycle, so the interval can
 * be changed at runtime (via {@code PUT /api/controller}) without a restart.
 * Replaces a static {@code @Scheduled(fixedDelayString=...)} on the poller.
 */
@Configuration
@RequiredArgsConstructor
public class ControllerSchedulingConfig implements SchedulingConfigurer {

    private final MetricsPoller metricsPoller;
    private final ControllerSettings settings;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(
                metricsPoller::poll,
                triggerContext -> {
                    Instant last = triggerContext.lastCompletion();
                    Instant base = (last != null) ? last : Instant.now();
                    return base.plusMillis(settings.getPollIntervalMs());
                }
        );
    }
}
