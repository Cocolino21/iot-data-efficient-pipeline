package com.licenta.traffic_control.scheduler;

import com.licenta.traffic_control.client.MqttActuator;
import com.licenta.traffic_control.config.CalibrationSettings;
import com.licenta.traffic_control.repository.CalibrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CalibrationOrchestrator {

    private final CalibrationRepository repo;
    private final MqttActuator mqttActuator;
    private final CalibrationSettings settings;

    @Scheduled(fixedDelayString = "${calibration.poll-interval-ms}")
    public void tick() {
        try {
            repo.seedMissingRows();
            repo.flagDrift(settings.getDriftThreshold(), settings.getMinHours());
            repo.flagColdStart();
            repo.reclaimExpiredLeases(settings.getBaselineDays());
            claimNewWork();
        } catch (Exception e) {
            log.error("Calibration tick failed: {}", e.getMessage(), e);
        }
    }

    private void claimNewWork() {
        int slots = settings.getMaxConcurrent() - repo.countCollecting();
        if (slots <= 0) {
            return;
        }

        for (Map<String, Object> row : repo.findClaimable(slots)) {
            String datastreamId = (String) row.get("datastream_id");
            String thingId = (String) row.get("thing_id");
            Number driftScore = (Number) row.get("drift_score");
            int ttl = settings.getCollectionTtlSeconds();

            if (repo.claimLease(datastreamId, ttl) == 0) {
                continue;
            }
            mqttActuator.publishRawMode(thingId, datastreamId, ttl);
            log.info("Claimed calibration: datastream={}, thing={}, drift={}, ttl={}s",
                    datastreamId, thingId, driftScore, ttl);
        }
    }

    public String triggerNow(String datastreamId) {
        int ttl = settings.getCollectionTtlSeconds();
        if (repo.triggerLease(datastreamId, ttl) == 0) {
            return "not started: datastream unknown, already collecting, or no device address yet";
        }
        String thingId = repo.findThingId(datastreamId);
        mqttActuator.publishRawMode(thingId, datastreamId, ttl);
        log.info("Manual calibration trigger: datastream={}, thing={}, ttl={}s",
                datastreamId, thingId, ttl);
        return "collecting for " + ttl + "s on device " + thingId;
    }

    public Map<String, Object> getState(int page, int size, String q) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        int safePage = Math.max(page, 0);
        String like = "%" + q.trim() + "%";
        return Map.of(
                "items", repo.listState(like, safeSize, safePage * safeSize),
                "total", repo.countState(like),
                "page", safePage,
                "size", safeSize);
    }
}
