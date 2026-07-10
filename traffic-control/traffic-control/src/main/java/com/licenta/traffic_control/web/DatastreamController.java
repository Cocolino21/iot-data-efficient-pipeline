package com.licenta.traffic_control.web;

import com.licenta.traffic_control.config.ReconstructionSettings;
import com.licenta.traffic_control.reconstruction.ReconstructionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Read-only inspection endpoints for the admin UI: browse datastreams, view a
 * stream's baseline profile and its continuous-aggregate tiers. All tables here
 * are keyed by the external datastream id (e.g. "power-1"), same as
 * calibration_state.
 */
@RestController
@RequestMapping("/api/datastreams")
@RequiredArgsConstructor
public class DatastreamController {

    private final JdbcTemplate jdbc;
    private final ReconstructionService reconstructionService;
    private final ReconstructionSettings reconstructionSettings;

    // Whitelist: tier name -> continuous aggregate view. The view name is never
    // taken from raw user input.
    private static final Map<String, String> TIER_VIEWS = Map.of(
            "hourly", "energy_hourly",
            "daily", "energy_daily",
            "weekly", "energy_weekly",
            "monthly", "energy_monthly");

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String q) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        int safePage = Math.max(page, 0);
        String like = "%" + q.trim() + "%";
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT datastream_id, thing_id, status, needs_calibration,
                       drift_score, last_collected_at
                FROM calibration_state
                WHERE datastream_id ILIKE ? OR thing_id ILIKE ?
                ORDER BY datastream_id
                LIMIT ? OFFSET ?
                """, like, like, safeSize, safePage * safeSize);
        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM calibration_state WHERE datastream_id ILIKE ? OR thing_id ILIKE ?",
                Long.class, like, like);
        return Map.of("items", items, "total", total, "page", safePage, "size", safeSize);
    }

    @GetMapping("/{id}/baseline")
    public List<Map<String, Object>> baseline(@PathVariable String id) {
        return jdbc.queryForList("""
                SELECT hour_of_day, expected_value, recorded_at
                FROM device_baseline
                WHERE datastream_id = ? AND minute_bucket = 0
                ORDER BY hour_of_day
                """, id);
    }

    @GetMapping("/{id}/raw")
    public List<Map<String, Object>> raw(
            @PathVariable String id,
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to) {
        long toMs = to != null ? to : System.currentTimeMillis();
        long fromMs = from != null ? from : toMs - 15 * 60_000L;
        return jdbc.queryForList("""
                SELECT "timestamp", value
                FROM observation
                WHERE datastream_id = ? AND "timestamp" BETWEEN ? AND ?
                ORDER BY "timestamp"
                LIMIT 10000
                """, id, new Timestamp(fromMs), new Timestamp(toMs));
    }

    @GetMapping("/{id}/reconstructed")
    public Map<String, Object> reconstructed(
            @PathVariable String id,
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to) {
        long toMs = to != null ? to : System.currentTimeMillis();
        long fromMs = from != null ? from : toMs - 15 * 60_000L;
        return reconstructionService.reconstruct(id, fromMs, toMs);
    }

    @GetMapping("/{id}/aggregates")
    public List<Map<String, Object>> aggregates(
            @PathVariable String id,
            @RequestParam(defaultValue = "hourly") String tier,
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to) {
        String view = TIER_VIEWS.get(tier);
        if (view == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "tier must be one of " + TIER_VIEWS.keySet());
        }

        Instant toTs = to != null ? Instant.ofEpochMilli(to) : Instant.now();
        Instant fromTs = from != null ? Instant.ofEpochMilli(from) : toTs.minus(7, ChronoUnit.DAYS);

        if ("hourly".equals(tier)) {
            // Baseline-adjusted average: coverage-weighted blend of the
            // measured average and the hour-of-day expectation. Full-coverage
            // hours keep their data; sparse (heavily PIP-shed) hours are
            // pulled toward the baseline. NULL when the stream has no baseline.
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
                    reconstructionSettings.getSamplesPerHour(),
                    reconstructionSettings.getSamplesPerHour(),
                    reconstructionSettings.getSamplesPerHour(),
                    id, Timestamp.from(fromTs), Timestamp.from(toTs));
        }

        // Coarser tiers materialize avg_value directly; no baseline blending
        // (the baseline is hour-of-day resolution).
        return jdbc.queryForList("""
                SELECT bucket, avg_value, min_value, max_value, sample_count
                FROM %s
                WHERE datastream_id = ? AND bucket BETWEEN ? AND ?
                ORDER BY bucket
                LIMIT 2000
                """.formatted(view),
                id, Timestamp.from(fromTs), Timestamp.from(toTs));
    }
}
