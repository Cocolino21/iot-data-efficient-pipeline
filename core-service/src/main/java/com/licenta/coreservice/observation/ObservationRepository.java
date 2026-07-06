package com.licenta.coreservice.observation;

import com.licenta.coreservice.observation.dto.ObservationDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Repository
public class ObservationRepository {

    // Continuous-aggregate tiers, finest first. Ranges longer than the raw
    // window are served from the coarsest tier that still gives plenty of
    // points; each tier's refresh lag near "now" is filled from the tier below
    // (and ultimately from raw observations) — see readAdaptive/tiered.
    private record Tier(String name, String view, String width, String avgExpr) {}

    private static final List<Tier> TIERS = List.of(
            new Tier("hourly", "energy_hourly", "1 hour", "total_value / NULLIF(sample_count, 0)"),
            new Tier("daily", "energy_daily", "1 day", "avg_value"),
            new Tier("weekly", "energy_weekly", "1 week", "avg_value"),
            new Tier("monthly", "energy_monthly", "1 month", "avg_value"));

    private static final long RAW_MAX_MS = Duration.ofHours(48).toMillis();
    private static final long HOURLY_MAX_MS = Duration.ofDays(14).toMillis();
    private static final long DAILY_MAX_MS = Duration.ofDays(90).toMillis();
    private static final long WEEKLY_MAX_MS = Duration.ofDays(550).toMillis();

    private final JdbcTemplate jdbc;

    public ObservationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Serve a time range from the most appropriate tier: raw observations for
     * short ranges (bucketed to maxPoints, as before), continuous aggregates
     * for long ones. Aggregate tiers are independent of the raw table's 30-day
     * retention, so "last 3 months" keeps its history.
     */
    public List<ObservationDto> readAdaptive(String externalId, long fromMs, long toMs, int maxPoints) {
        long rangeMs = Math.max(toMs - fromMs, 1);
        if (rangeMs <= RAW_MAX_MS) return downsample(externalId, fromMs, toMs, maxPoints);
        int tierIdx = rangeMs <= HOURLY_MAX_MS ? 0
                : rangeMs <= DAILY_MAX_MS ? 1
                : rangeMs <= WEEKLY_MAX_MS ? 2
                : 3;
        return tiered(externalId, fromMs, toMs, tierIdx);
    }

    public List<ObservationDto> downsample(String externalId, long fromMs, long toMs, int maxPoints) {
        long rangeMs = Math.max(toMs - fromMs, 1);
        long bucketSeconds = Math.max(1, (rangeMs / Math.max(1, maxPoints)) / 1000);

        return jdbc.query(
                "WITH bucketed AS (" +
                "  SELECT time_bucket(make_interval(secs => ?), \"timestamp\") AS bucket, " +
                "         AVG(value) AS value " +
                "    FROM observation " +
                "   WHERE datastream_id = ? AND \"timestamp\" BETWEEN ? AND ? " +
                "   GROUP BY 1" +
                ") " +
                "SELECT bucket, value FROM bucketed ORDER BY bucket",
                (rs, i) -> new ObservationDto(rs.getTimestamp("bucket").getTime(), rs.getDouble("value")),
                bucketSeconds,
                externalId,
                new Timestamp(fromMs),
                new Timestamp(toMs));
    }

    /**
     * Stitch the selected aggregate tier with every finer tier below it.
     *
     * Each aggregate lags behind "now" by its refresh policy, so we compute a
     * per-tier watermark W = max(bucket) + bucket_width (= end of materialized
     * data; falls back to the coarser tier's watermark, and ultimately to
     * 'from', when the view is empty). The result is one UNION ALL query where
     * the selected tier covers [from, W_selected), each finer tier covers
     * [W_coarser, W_finer), and raw observations (bucketed hourly) cover the
     * final stretch up to 'to'. Mixed bucket widths are fine: the chart plots
     * (timestamp, value) points.
     */
    private List<ObservationDto> tiered(String externalId, long fromMs, long toMs, int tierIdx) {
        Timestamp from = new Timestamp(fromMs);
        Timestamp to = new Timestamp(toMs);
        List<Object> args = new ArrayList<>();

        // Watermark CTEs, coarsest (selected) tier first.
        StringBuilder sql = new StringBuilder("WITH ");
        for (int i = tierIdx; i >= 0; i--) {
            Tier t = TIERS.get(i);
            String fallback = i == tierIdx
                    ? "?::timestamptz"
                    : "(SELECT w FROM w_" + TIERS.get(i + 1).name() + ")";
            sql.append("w_").append(t.name())
                    .append(" AS (SELECT COALESCE(max(bucket) + interval '").append(t.width())
                    .append("', ").append(fallback).append(") AS w FROM ").append(t.view())
                    .append(" WHERE datastream_id = ?)")
                    .append(i > 0 ? ", " : " ");
            if (i == tierIdx) args.add(from);
            args.add(externalId);
        }

        // One arm per tier, plus the raw tail.
        List<String> arms = new ArrayList<>();
        for (int i = tierIdx; i >= 0; i--) {
            Tier t = TIERS.get(i);
            String start = i == tierIdx
                    ? "?::timestamptz"
                    : "GREATEST((SELECT w FROM w_" + TIERS.get(i + 1).name() + "), ?::timestamptz)";
            arms.add("SELECT bucket, " + t.avgExpr() + " AS value FROM " + t.view()
                    + " WHERE datastream_id = ? AND bucket >= " + start
                    + " AND bucket < LEAST((SELECT w FROM w_" + t.name() + "), ?::timestamptz)");
            args.add(externalId);
            args.add(from);
            args.add(to);
        }
        arms.add("SELECT time_bucket(interval '1 hour', \"timestamp\") AS bucket, AVG(value) AS value"
                + " FROM observation WHERE datastream_id = ?"
                + " AND \"timestamp\" >= GREATEST((SELECT w FROM w_hourly), ?::timestamptz)"
                + " AND \"timestamp\" <= ? GROUP BY 1");
        args.add(externalId);
        args.add(from);
        args.add(to);

        sql.append("SELECT bucket, value FROM (")
                .append(String.join(" UNION ALL ", arms))
                .append(") t WHERE value IS NOT NULL ORDER BY bucket");

        return jdbc.query(sql.toString(),
                (rs, i) -> new ObservationDto(rs.getTimestamp("bucket").getTime(), rs.getDouble("value")),
                args.toArray());
    }
}
