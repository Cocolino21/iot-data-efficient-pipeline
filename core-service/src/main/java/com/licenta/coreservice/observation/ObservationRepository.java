package com.licenta.coreservice.observation;

import com.licenta.coreservice.observation.dto.ObservationDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class ObservationRepository {

    private final JdbcTemplate jdbc;

    public ObservationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
}
