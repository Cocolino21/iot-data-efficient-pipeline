package com.licenta.traffic_control.poller;

import com.licenta.traffic_control.actuator.EmqxApiClient;
import com.licenta.traffic_control.config.EmqxTuningSettings;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmqxDropsPoller {

    public enum State { NORMAL, BOOSTED }

    private final PrometheusClient prometheusClient;
    private final EmqxApiClient emqxApiClient;
    private final EmqxTuningSettings settings;

    @Getter
    private volatile State state = State.NORMAL;
    @Getter
    private volatile double lastDropRate = 0;
    private int consecutiveZeroPolls = 0;

    @Scheduled(fixedDelayString = "${controller.poll-interval-ms}")
    public void poll() {
        if (!settings.isEnabled()) {
            return;
        }

        try {
            lastDropRate = prometheusClient.getEmqxDropRate();
            log.info("EMQX drop rate={}, state={}", String.format("%.2f", lastDropRate), state);

            if (lastDropRate > settings.getDropRateThreshold()) {
                consecutiveZeroPolls = 0;
                if (state == State.NORMAL) {
                    log.warn("Drops detected (rate={}), boosting EMQX bridge params",
                            String.format("%.2f", lastDropRate));
                    emqxApiClient.pushUpperLimits();
                    state = State.BOOSTED;
                }
            } else {
                if (state == State.BOOSTED) {
                    consecutiveZeroPolls++;
                    if (consecutiveZeroPolls >= settings.getCooldownPolls()) {
                        log.info("No drops for {} consecutive polls, restoring EMQX defaults",
                                consecutiveZeroPolls);
                        emqxApiClient.restoreDefaults();
                        state = State.NORMAL;
                        consecutiveZeroPolls = 0;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to poll EMQX drop rate: {}", e.getMessage());
        }
    }
}
