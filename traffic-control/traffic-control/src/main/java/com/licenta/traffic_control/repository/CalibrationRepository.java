package com.licenta.traffic_control.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class CalibrationRepository {

    private final JdbcTemplate jdbc;

    public void seedMissingRows() {
        jdbc.execute("""
                INSERT INTO calibration_state (datastream_id)
                SELECT DISTINCT h.datastream_id
                FROM energy_hourly h
                ON CONFLICT (datastream_id) DO NOTHING
                """);
    }

    public void flagDrift(double driftThreshold, int minHours) {
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
                """, driftThreshold, minHours);
    }

    public void flagColdStart() {
        jdbc.execute("""
                UPDATE calibration_state cs
                SET needs_calibration = TRUE, drift_score = 9999, flagged_at = NOW()
                WHERE cs.status = 'idle'
                  AND cs.needs_calibration = FALSE
                  AND NOT EXISTS (SELECT 1 FROM device_baseline b
                                  WHERE b.datastream_id = cs.datastream_id)
                """);
    }

    public void reclaimExpiredLeases(int baselineDays) {
        jdbc.queryForList("SELECT cbl_reclaim_leases(?)", baselineDays);
    }

    public int countCollecting() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM calibration_state WHERE status = 'collecting'", Integer.class);
    }

    public List<Map<String, Object>> findClaimable(int slots) {
        return jdbc.queryForList("""
                SELECT datastream_id, thing_id, drift_score
                FROM calibration_state
                WHERE needs_calibration = TRUE AND status = 'idle'
                  AND thing_id IS NOT NULL
                ORDER BY drift_score DESC
                LIMIT ?
                """, slots);
    }

    public int claimLease(String datastreamId, int ttlSeconds) {
        return jdbc.update("""
                UPDATE calibration_state
                SET status = 'collecting',
                    lease_started_at = NOW(),
                    lease_expires_at = NOW() + make_interval(secs => ?)
                WHERE datastream_id = ? AND status = 'idle'
                """, ttlSeconds, datastreamId);
    }

    public int triggerLease(String datastreamId, int ttlSeconds) {
        return jdbc.update("""
                UPDATE calibration_state
                SET status = 'collecting',
                    needs_calibration = TRUE,
                    flagged_at = NOW(),
                    lease_started_at = NOW(),
                    lease_expires_at = NOW() + make_interval(secs => ?)
                WHERE datastream_id = ? AND status = 'idle' AND thing_id IS NOT NULL
                """, ttlSeconds, datastreamId);
    }

    public String findThingId(String datastreamId) {
        return jdbc.queryForObject(
                "SELECT thing_id FROM calibration_state WHERE datastream_id = ?",
                String.class, datastreamId);
    }

    public List<Map<String, Object>> listState(String like, int size, int offset) {
        return jdbc.queryForList("""
                SELECT datastream_id, thing_id, status, needs_calibration,
                       drift_score, flagged_at, lease_started_at, lease_expires_at, last_collected_at
                FROM calibration_state
                WHERE datastream_id ILIKE ? OR thing_id ILIKE ?
                ORDER BY drift_score DESC, datastream_id
                LIMIT ? OFFSET ?
                """, like, like, size, offset);
    }

    public long countState(String like) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM calibration_state WHERE datastream_id ILIKE ? OR thing_id ILIKE ?",
                Long.class, like, like);
    }
}
