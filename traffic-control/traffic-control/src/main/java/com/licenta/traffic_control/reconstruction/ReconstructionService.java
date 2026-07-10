package com.licenta.traffic_control.reconstruction;

import com.licenta.traffic_control.config.ReconstructionSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fills gaps left by the PIP filter with linearly interpolated points, so the
 * raw chart shows a continuous (but honestly flagged) trace. Baseline
 * knowledge is deliberately NOT used here — the baseline is hour-of-day
 * resolution, so its rightful place is the hourly aggregate adjustment
 * (see DatastreamController.aggregates), not sub-hour point synthesis.
 */
@Service
@RequiredArgsConstructor
public class ReconstructionService {

    private final JdbcTemplate jdbc;
    private final ReconstructionSettings settings;

    public record Point(long timestamp, double value, boolean reconstructed) {}

    public Map<String, Object> reconstruct(String datastreamId, long fromMs, long toMs) {
        List<Point> measured = jdbc.query("""
                SELECT "timestamp", value
                FROM observation
                WHERE datastream_id = ? AND "timestamp" BETWEEN ? AND ?
                ORDER BY "timestamp"
                LIMIT 10000
                """,
                (rs, i) -> new Point(rs.getTimestamp(1).getTime(), rs.getDouble(2), false),
                datastreamId, new Timestamp(fromMs), new Timestamp(toMs));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("points", fillGaps(measured));
        out.put("method", "interpolation");
        return out;
    }

    private List<Point> fillGaps(List<Point> measured) {
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
                out.add(new Point(t, p.value() + alpha * (next.value() - p.value()), true));
            }
        }
        return out;
    }
}
