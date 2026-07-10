package com.licenta.traffic_control.repository;

import com.licenta.traffic_control.dto.Observation;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class DatastreamRepository {

    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> listStreams(String like, int size, int offset) {
        return jdbc.queryForList("""
                SELECT datastream_id, thing_id, status, needs_calibration,
                       drift_score, last_collected_at
                FROM calibration_state
                WHERE datastream_id ILIKE ? OR thing_id ILIKE ?
                ORDER BY datastream_id
                LIMIT ? OFFSET ?
                """, like, like, size, offset);
    }

    public long countStreams(String like) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM calibration_state WHERE datastream_id ILIKE ? OR thing_id ILIKE ?",
                Long.class, like, like);
    }

    public List<Map<String, Object>> findBaseline(String datastreamId) {
        return jdbc.queryForList("""
                SELECT hour_of_day, expected_value, recorded_at
                FROM device_baseline
                WHERE datastream_id = ? AND minute_bucket = 0
                ORDER BY hour_of_day
                """, datastreamId);
    }

    public List<Observation> findObservations(String datastreamId, long fromMs, long toMs) {
        return jdbc.query("""
                SELECT "timestamp", value
                FROM observation
                WHERE datastream_id = ? AND "timestamp" BETWEEN ? AND ?
                ORDER BY "timestamp"
                LIMIT 10000
                """,
                (rs, i) -> new Observation(rs.getTimestamp(1).getTime(), rs.getDouble(2)),
                datastreamId, new Timestamp(fromMs), new Timestamp(toMs));
    }

    public List<Map<String, Object>> findHourlyAdjusted(String datastreamId, long fromMs, long toMs,
                                                         double samplesPerHour) {
        return jdbc.queryForList("""
                SELECT h.bucket,
                       h.total_value / NULLIF(h.sample_count, 0) AS avg_value,
                       h.min_value, h.max_value, h.sample_count,
                       b.expected_value,
                       LEAST(1.0, h.sample_count / ?) AS coverage,
                       LEAST(1.0, h.sample_count / ?) * (h.total_value / NULLIF(h.sample_count, 0))
                         + (1 - LEAST(1.0, h.sample_count / ?)) * b.expected_value AS adjusted_avg
                FROM energy_hourly h
                LEFT JOIN device_baseline b
                  ON  b.datastream_id = h.datastream_id
                  AND b.hour_of_day   = EXTRACT(HOUR FROM h.bucket)::INT
                  AND b.minute_bucket = 0
                WHERE h.datastream_id = ? AND h.bucket BETWEEN ? AND ?
                ORDER BY h.bucket
                LIMIT 2000
                """,
                samplesPerHour, samplesPerHour, samplesPerHour,
                datastreamId, new Timestamp(fromMs), new Timestamp(toMs));
    }

    public List<Map<String, Object>> findAggregates(String view, String datastreamId,
                                                     long fromMs, long toMs) {
        return jdbc.queryForList("""
                SELECT bucket, avg_value, min_value, max_value, sample_count
                FROM %s
                WHERE datastream_id = ? AND bucket BETWEEN ? AND ?
                ORDER BY bucket
                LIMIT 2000
                """.formatted(view),
                datastreamId, new Timestamp(fromMs), new Timestamp(toMs));
    }
}
