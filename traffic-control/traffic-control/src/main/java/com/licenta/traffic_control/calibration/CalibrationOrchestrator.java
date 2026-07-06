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
        // Ensure every known datastream has a calibration_state row. thing_id is
        // deliberately left NULL here: the sink-service fills it with the device's
        // *external* id (from live telemetry), which is what MQTT addressing needs —
        // the registry's UUID would not match any device's cmd/control subscription.
        jdbc.execute("""
                INSERT INTO calibration_state (datastream_id)
                SELECT DISTINCT h.datastream_id
                FROM energy_hourly h
                ON CONFLICT (datastream_id) DO NOTHING
                """);

        // Drift: flag datastreams whose recent hourly averages deviate from baseline.
        // The score is relative — mean |actual - expected| divided by the mean
        // expected level over the same hours — so one threshold works for every
        // sensor unit (0.15 = "15% off its own typical level").
        jdbc.update("""
                UPDATE calibration_state cs
                SET needs_calibration = TRUE, drift_score = d.drift_score, flagged_at = NOW()
                FROM (
                    SELECT h.datastream_id,
                           AVG(ABS(h.total_value / h.sample_count - b.expected_value))
                               / NULLIF(AVG(ABS(b.expected_value)), 0) AS drift_score,
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
                  AND d.drift_score      > ?
                  AND d.n_hours          > ?
                  AND cs.status            = 'idle'
                  AND cs.needs_calibration = FALSE
                  AND (cs.last_collected_at IS NULL
                       OR cs.last_collected_at < NOW() - interval '1 day')
                """, settings.getDriftThreshold(), settings.getMinHours());

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

        // thing_id IS NOT NULL: without a known device address the go-raw command
        // can't be sent, and leasing anyway would rebuild the baseline from
        // PIP-filtered data. The sink fills thing_id as soon as telemetry flows.
        List<Map<String, Object>> candidates = jdbc.queryForList("""
                SELECT datastream_id, thing_id, drift_score
                FROM calibration_state
                WHERE needs_calibration = TRUE AND status = 'idle'
                  AND thing_id IS NOT NULL
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

            mqttActuator.publishRawMode(thingId, datastreamId, ttl);

            log.info("Claimed calibration: datastream={}, thing={}, drift={}, ttl={}s",
                    datastreamId, thingId, driftScore, ttl);
        }
    }

    /**
     * Immediately lease the given datastream and command its device into raw
     * mode, bypassing the drift/flag queue. Used by the admin UI ("Calibrate
     * now") so a full flag→raw→ingest→rebuild cycle is demonstrable live with
     * a short collection TTL.
     */
    public String triggerNow(String datastreamId) {
        int ttl = settings.getCollectionTtlSeconds();

        int updated = jdbc.update("""
                UPDATE calibration_state
                SET status = 'collecting',
                    needs_calibration = TRUE,
                    flagged_at = NOW(),
                    lease_started_at = NOW(),
                    lease_expires_at = NOW() + make_interval(secs => ?)
                WHERE datastream_id = ? AND status = 'idle' AND thing_id IS NOT NULL
                """, ttl, datastreamId);

        if (updated == 0) {
            return "not started: datastream unknown, already collecting, or no device address yet";
        }

        String thingId = jdbc.queryForObject(
                "SELECT thing_id FROM calibration_state WHERE datastream_id = ?",
                String.class, datastreamId);
        mqttActuator.publishRawMode(thingId, datastreamId, ttl);

        log.info("Manual calibration trigger: datastream={}, thing={}, ttl={}s",
                datastreamId, thingId, ttl);
        return "collecting for " + ttl + "s on device " + thingId;
    }
}
