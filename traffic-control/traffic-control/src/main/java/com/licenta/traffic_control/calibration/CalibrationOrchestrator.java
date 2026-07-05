package com.licenta.traffic_control.calibration;

import com.licenta.traffic_control.actuator.MqttActuator;
import com.licenta.traffic_control.config.CalibrationSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CalibrationOrchestrator {

    private final JdbcTemplate jdbc;
    private final MqttActuator mqttActuator;
    private final CalibrationSettings settings;

    @Scheduled(fixedDelayString = "${calibration.poll-interval-ms}")
    public void tick() {
        try {
            flagNeedsCalibration();
            reclaimExpiredLeases();
            claimNewWork();
        } catch (Exception e) {
            log.error("Calibration tick failed: {}", e.getMessage(), e);
        }
    }

    private void flagNeedsCalibration() {
        // Ensure every known datastream has a calibration_state row
        jdbc.execute("""
                INSERT INTO calibration_state (datastream_id, thing_id)
                SELECT DISTINCT h.datastream_id, ds.thing_id::TEXT
                FROM energy_hourly h
                LEFT JOIN datastream ds ON ds.datastream_id = h.datastream_id
                ON CONFLICT (datastream_id) DO NOTHING
                """);

        // Drift: flag datastreams whose recent hourly averages deviate from baseline
        jdbc.execute("""
                UPDATE calibration_state cs
                SET needs_calibration = TRUE, drift_score = d.drift_score, flagged_at = NOW()
                FROM (
                    SELECT h.datastream_id,
                           AVG(ABS(h.total_value / h.sample_count - b.expected_value)) AS drift_score,
                           COUNT(*) AS n_hours
                    FROM energy_hourly h
                    JOIN device_baseline b
                      ON  b.datastream_id = h.datastream_id
                      AND b.hour_of_day   = EXTRACT(HOUR FROM h.bucket)::INT
                      AND b.minute_bucket = 0
                    WHERE h.bucket > NOW() - interval '3 days'
                    GROUP BY h.datastream_id
                ) d
                WHERE cs.datastream_id   = d.datastream_id
                  AND d.drift_score      > 0.3
                  AND d.n_hours          > 24
                  AND cs.status            = 'idle'
                  AND cs.needs_calibration = FALSE
                  AND (cs.last_collected_at IS NULL
                       OR cs.last_collected_at < NOW() - interval '1 day')
                """);

        // Cold start: datastreams with no baseline yet get highest priority
        jdbc.execute("""
                UPDATE calibration_state cs
                SET needs_calibration = TRUE, drift_score = 9999, flagged_at = NOW()
                WHERE cs.status = 'idle'
                  AND cs.needs_calibration = FALSE
                  AND NOT EXISTS (SELECT 1 FROM device_baseline b
                                  WHERE b.datastream_id = cs.datastream_id)
                """);
    }

    private void reclaimExpiredLeases() {
        jdbc.execute("SELECT cbl_reclaim_leases(" + settings.getBaselineDays() + ")");
    }

    private void claimNewWork() {
        int currentlyCollecting = jdbc.queryForObject(
                "SELECT COUNT(*) FROM calibration_state WHERE status = 'collecting'",
                Integer.class
        );
        int slots = settings.getMaxConcurrent() - currentlyCollecting;
        if (slots <= 0) {
            return;
        }

        List<Map<String, Object>> candidates = jdbc.queryForList("""
                SELECT datastream_id, thing_id, drift_score
                FROM calibration_state
                WHERE needs_calibration = TRUE AND status = 'idle'
                ORDER BY drift_score DESC
                LIMIT ?
                """, slots);

        for (Map<String, Object> row : candidates) {
            String datastreamId = (String) row.get("datastream_id");
            String thingId = (String) row.get("thing_id");
            Number driftScore = (Number) row.get("drift_score");

            int ttl = settings.getCollectionTtlSeconds();

            int updated = jdbc.update("""
                    UPDATE calibration_state
                    SET status = 'collecting',
                        lease_started_at = NOW(),
                        lease_expires_at = NOW() + make_interval(secs => ?)
                    WHERE datastream_id = ? AND status = 'idle'
                    """, ttl, datastreamId);

            if (updated == 0) {
                continue;
            }

            if (thingId != null) {
                mqttActuator.publishRawMode(thingId, datastreamId, ttl);
            }

            log.info("Claimed calibration: datastream={}, thing={}, drift={}, ttl={}s",
                    datastreamId, thingId, driftScore, ttl);
        }
    }
}
