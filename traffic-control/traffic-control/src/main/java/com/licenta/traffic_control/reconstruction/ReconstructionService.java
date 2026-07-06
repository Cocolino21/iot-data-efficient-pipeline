package com.licenta.traffic_control.reconstruction;

import com.licenta.traffic_control.config.ReconstructionSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fills gaps left by the PIP filter with synthetic points.
 *
 * Energy streams with a trustworthy baseline get baseline-shaped
 * reconstruction: r(t) = b(t) + offset(t), where b(t) is the hour-of-day
 * expected value (linearly interpolated between hour buckets) and offset(t)
 * linearly blends the anchor errors (v - b) at the gap's two measured
 * endpoints. This follows the diurnal shape across long gaps yet still passes
 * exactly through both measured anchors; when the baseline is flat it reduces
 * to plain linear interpolation. All other cases — non-energy streams, no
 * baseline yet, or a baseline the live data currently drifts away from —
 * use plain linear interpolation.
 */
@Service
@RequiredArgsConstructor
public class ReconstructionService {

    private final JdbcTemplate jdbc;
    private final ReconstructionSettings settings;

    public record Point(long timestamp, double value, boolean reconstructed) {}

    public Map<String, Object> reconstruct(String datastreamId, int minutes) {
        List<Point> measured = jdbc.query("""
                SELECT "timestamp", value
                FROM observation
                WHERE datastream_id = ? AND "timestamp" > NOW() - make_interval(mins => ?)
                ORDER BY "timestamp"
                LIMIT 10000
                """,
                (rs, i) -> new Point(rs.getTimestamp(1).getTime(), rs.getDouble(2), false),
                datastreamId, minutes);

        boolean energy = isEnergy(datastreamId);
        double[] baseline = energy ? loadBaseline(datastreamId) : null;
        Double drift = baseline != null ? currentDrift(datastreamId) : null;
        boolean baselineTrusted = baseline != null
                && (drift == null || drift <= settings.getMaxDrift());
        boolean useBaseline = energy && baselineTrusted && baseline != null;

        List<Point> points = fillGaps(measured, useBaseline ? baseline : null);

        String method = !energy ? "interpolation (non-energy stream)"
                : baseline == null ? "interpolation (no baseline yet)"
                : !baselineTrusted ? "interpolation (baseline drift %.2f > %.2f — awaiting recalibration)"
                        .formatted(drift, settings.getMaxDrift())
                : "baseline" + (drift != null ? " (drift %.2f)".formatted(drift) : "");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("points", points);
        out.put("method", method);
        out.put("energy", energy);
        out.put("baselineTrusted", baselineTrusted);
        out.put("drift", drift);
        return out;
    }

    private boolean isEnergy(String datastreamId) {
        List<String> types = jdbc.queryForList(
                "SELECT observation_type FROM datastream WHERE datastream_id = ?",
                String.class, datastreamId);
        return !types.isEmpty() && "energy".equalsIgnoreCase(types.getFirst());
    }

    /** Expected value per hour-of-day, or null unless all 24 hours are present. */
    private double[] loadBaseline(String datastreamId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT hour_of_day, expected_value
                FROM device_baseline
                WHERE datastream_id = ? AND minute_bucket = 0
                """, datastreamId);
        if (rows.size() < 24) return null;
        double[] byHour = new double[24];
        for (Map<String, Object> r : rows) {
            byHour[((Number) r.get("hour_of_day")).intValue()] = ((Number) r.get("expected_value")).doubleValue();
        }
        return byHour;
    }

    /** Same relative drift the calibration flagger uses, for this one stream. */
    private Double currentDrift(String datastreamId) {
        return jdbc.queryForObject("""
                SELECT AVG(ABS(h.total_value / h.sample_count - b.expected_value))
                       / NULLIF(AVG(ABS(b.expected_value)), 0)
                FROM energy_hourly h
                JOIN device_baseline b
                  ON  b.datastream_id = h.datastream_id
                  AND b.hour_of_day   = EXTRACT(HOUR FROM h.bucket)::INT
                  AND b.minute_bucket = 0
                WHERE h.datastream_id = ? AND h.bucket > NOW() - interval '24 hours'
                """, Double.class, datastreamId);
    }

    private List<Point> fillGaps(List<Point> measured, double[] baseline) {
        long gapMs = settings.getGapSeconds() * 1000L;
        List<Point> out = new ArrayList<>(measured.size());
        for (int i = 0; i < measured.size(); i++) {
            Point p = measured.get(i);
            out.add(p);
            if (i + 1 >= measured.size()) break;
            Point next = measured.get(i + 1);
            long gap = next.timestamp() - p.timestamp();
            if (gap <= gapMs) continue;

            long step = Math.max(1000, gap / settings.getMaxPointsPerGap());
            for (long t = p.timestamp() + step; t < next.timestamp(); t += step) {
                double alpha = (double) (t - p.timestamp()) / gap;
                double value;
                if (baseline != null) {
                    // Baseline shape + linearly blended anchor offsets.
                    double bT = baselineAt(baseline, t);
                    double offset = (1 - alpha) * (p.value() - baselineAt(baseline, p.timestamp()))
                            + alpha * (next.value() - baselineAt(baseline, next.timestamp()));
                    value = bT + offset;
                } else {
                    value = p.value() + alpha * (next.value() - p.value());
                }
                out.add(new Point(t, value, true));
            }
        }
        return out;
    }

    /**
     * Hour-of-day baseline lookup, linearly interpolated between hour buckets
     * (wrapping midnight). Hours are taken in UTC, matching cbl_ingest_day's
     * EXTRACT(HOUR ...) under the containers' default UTC session timezone.
     */
    private double baselineAt(double[] baseline, long epochMs) {
        var zdt = Instant.ofEpochMilli(epochMs).atZone(ZoneOffset.UTC);
        int h = zdt.getHour();
        double frac = (zdt.getMinute() + zdt.getSecond() / 60.0) / 60.0;
        return baseline[h] * (1 - frac) + baseline[(h + 1) % 24] * frac;
    }
}
