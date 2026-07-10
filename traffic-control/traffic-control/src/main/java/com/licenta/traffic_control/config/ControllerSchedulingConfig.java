package com.licenta.traffic_control.config;

import com.licenta.traffic_control.scheduler.MetricsPoller;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Instant;

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
