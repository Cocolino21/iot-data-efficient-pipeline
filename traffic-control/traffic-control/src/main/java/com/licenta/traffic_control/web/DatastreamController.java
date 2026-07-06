package com.licenta.traffic_control.web;

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
            @RequestParam(defaultValue = "15") int minutes) {
        int safeMinutes = Math.min(Math.max(minutes, 1), 180);
        return jdbc.queryForList("""
                SELECT "timestamp", value
                FROM observation
                WHERE datastream_id = ? AND "timestamp" > NOW() - make_interval(mins => ?)
                ORDER BY "timestamp"
                LIMIT 10000
                """, id, safeMinutes);
    }

    @GetMapping("/{id}/reconstructed")
    public Map<String, Object> reconstructed(
            @PathVariable String id,
            @RequestParam(defaultValue = "15") int minutes) {
        int safeMinutes = Math.min(Math.max(minutes, 1), 180);
        return reconstructionService.reconstruct(id, safeMinutes);
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

        // energy_hourly carries no avg_value column; derive it. The coarser
        // tiers materialize avg_value directly.
        String avgExpr = "hourly".equals(tier)
                ? "total_value / NULLIF(sample_count, 0)"
                : "avg_value";

        return jdbc.queryForList("""
                SELECT bucket, %s AS avg_value, min_value, max_value, sample_count
                FROM %s
                WHERE datastream_id = ? AND bucket BETWEEN ? AND ?
                ORDER BY bucket
                LIMIT 2000
                """.formatted(avgExpr, view),
                id, Timestamp.from(fromTs), Timestamp.from(toTs));
    }
}
