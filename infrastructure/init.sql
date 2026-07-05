CREATE TABLE IF NOT EXISTS "user"
(
    uuid
    UUID
    PRIMARY
    KEY
    DEFAULT
    gen_random_uuid
(
),
    name VARCHAR
(
    255
) NOT NULL,
    email VARCHAR
(
    255
) UNIQUE NOT NULL,
    oauth_provider VARCHAR
(
    50
),
    oauth_provider_id VARCHAR
(
    255
),
    created_at TIMESTAMPTZ DEFAULT NOW
(
)
    );

CREATE TABLE IF NOT EXISTS thing
(
    uuid
    UUID
    PRIMARY
    KEY
    DEFAULT
    gen_random_uuid
(
),
    user_id UUID REFERENCES "user"
(
    uuid
),
    name VARCHAR
(
    255
) NOT NULL,
    description TEXT,
    longitude DOUBLE PRECISION,
    latitude DOUBLE PRECISION,
    status VARCHAR
(
    50
) DEFAULT 'active',
    last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW
(
)
    );

CREATE TABLE IF NOT EXISTS sensor
(
    uuid
    UUID
    PRIMARY
    KEY
    DEFAULT
    gen_random_uuid
(
),
    name VARCHAR
(
    255
) NOT NULL,
    description TEXT,
    metadata JSONB
    );

CREATE TABLE IF NOT EXISTS observed_property
(
    uuid
    UUID
    PRIMARY
    KEY
    DEFAULT
    gen_random_uuid
(
),
    name VARCHAR
(
    255
) NOT NULL,
    definition TEXT,
    unit_of_measurement VARCHAR
(
    50
),
    symbol VARCHAR
(
    10
)
    );

CREATE TABLE IF NOT EXISTS datastream
(
    uuid
    UUID
    PRIMARY
    KEY
    DEFAULT
    gen_random_uuid
(
),
    thing_id UUID REFERENCES thing
(
    uuid
),
    sensor_id UUID REFERENCES sensor
(
    uuid
),
    observed_property_id UUID REFERENCES observed_property
(
    uuid
),
    name VARCHAR
(
    255
) NOT NULL,
    observation_type VARCHAR
(
    50
),
    is_active BOOLEAN DEFAULT TRUE,
    datastream_id TEXT UNIQUE NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW
(
)
    );

-- thing_id, sensor_id, observed_property_id are intentionally nullable:
-- the sink-service auto-registers unknown datastreams with minimal info
-- so that high-throughput COPY inserts never fail on FK violations.
-- These columns are filled in later via the management API.

-- Auto-register placeholder datastream rows for unknown IDs.
-- Called by the sink-service before each batch COPY into observation.
CREATE OR REPLACE FUNCTION ensure_datastreams(ids TEXT[])
    RETURNS void AS $$
    INSERT INTO datastream (datastream_id, name)
    SELECT id, id FROM unnest(ids) AS id
    ON CONFLICT (datastream_id) DO NOTHING;
$$ LANGUAGE SQL;

CREATE TABLE IF NOT EXISTS observation
(
    "timestamp"
    TIMESTAMPTZ
    NOT
    NULL,
    datastream_id
    TEXT
    NOT
    NULL
    REFERENCES datastream (datastream_id),
    value
    DOUBLE
    PRECISION
    NOT
    NULL
);

SELECT create_hypertable('observation', 'timestamp', if_not_exists => TRUE);

CREATE INDEX IF NOT EXISTS idx_observation_datastream
    ON observation (datastream_id, "timestamp" DESC);

ALTER TABLE observation SET (
    timescaledb.compress,
    timescaledb.compress_orderby = '"timestamp" DESC',
    timescaledb.compress_segmentby = 'datastream_id'
    );

SELECT add_compression_policy('observation', interval '7 days', if_not_exists => true);

CREATE
MATERIALIZED VIEW energy_hourly
WITH (timescaledb.continuous) AS
SELECT time_bucket('1 hour', o."timestamp") AS bucket,
       o.datastream_id,
       SUM(o.value)                         AS total_value,
       MIN(o.value)                         AS min_value,
       MAX(o.value)                         AS max_value,
       COUNT(*)                             AS sample_count
FROM observation o
GROUP BY bucket, o.datastream_id;

CREATE
MATERIALIZED VIEW energy_daily
WITH (timescaledb.continuous) AS
SELECT time_bucket('1 day', bucket) AS bucket,
       datastream_id,
       SUM(total_value)             AS total_value,
       SUM(total_value) /
       NULLIF(SUM(sample_count), 0) AS avg_value,
       MIN(min_value)               AS min_value,
       MAX(max_value)               AS max_value,
       SUM(sample_count)            AS sample_count
FROM energy_hourly
GROUP BY 1, 2;

CREATE
MATERIALIZED VIEW energy_weekly
WITH (timescaledb.continuous) AS
SELECT time_bucket('1 week', bucket) AS bucket,
       datastream_id,
       SUM(total_value)              AS total_value,
       SUM(total_value) /
       NULLIF(SUM(sample_count), 0)  AS avg_value,
       MIN(min_value)                AS min_value,
       MAX(max_value)                AS max_value,
       SUM(sample_count)             AS sample_count
FROM energy_daily
GROUP BY 1, 2;

CREATE
MATERIALIZED VIEW energy_monthly
WITH (timescaledb.continuous) AS
SELECT time_bucket('1 month', bucket) AS bucket,
       datastream_id,
       SUM(total_value)               AS total_value,
       SUM(total_value) /
       NULLIF(SUM(sample_count), 0)   AS avg_value,
       MIN(min_value)                 AS min_value,
       MAX(max_value)                 AS max_value,
       SUM(sample_count)              AS sample_count
FROM energy_daily
GROUP BY 1, 2;

SELECT add_continuous_aggregate_policy('energy_hourly',
                                       start_offset => interval '3 hours',
                                       end_offset => interval '1 hour',
                                       schedule_interval => interval '30 minutes',
                                       if_not_exists => true
       );

SELECT add_continuous_aggregate_policy('energy_daily',
                                       start_offset => interval '3 days',
                                       end_offset => interval '1 day',
                                       schedule_interval => interval '1 hour',
                                       if_not_exists => true
       );

SELECT add_continuous_aggregate_policy('energy_weekly',
                                       start_offset => interval '3 weeks',
                                       end_offset => interval '1 week',
                                       schedule_interval => interval '6 hours',
                                       if_not_exists => true
       );

SELECT add_continuous_aggregate_policy('energy_monthly',
                                       start_offset => interval '3 months',
                                       end_offset => interval '1 month',
                                       schedule_interval => interval '1 day',
                                       if_not_exists => true
       );

SELECT add_retention_policy('observation', interval '30 days', if_not_exists => true);

CREATE
OR REPLACE FUNCTION energy_tiered(
    from_ts   TIMESTAMPTZ,
    to_ts     TIMESTAMPTZ
)
RETURNS TABLE (
    bucket            TIMESTAMPTZ,
    datastream_id     TEXT,
    total_value       DOUBLE PRECISION,
    avg_value         DOUBLE PRECISION,
    min_value         DOUBLE PRECISION,
    max_value         DOUBLE PRECISION,
    sample_count      BIGINT
) AS $$
-- Head: hourly granularity for the first (possibly partial) day
SELECT bucket,
       datastream_id,
       total_value,
       total_value / NULLIF(sample_count, 0),
       min_value,
       max_value,
       sample_count
FROM energy_hourly
WHERE bucket >= from_ts
  AND bucket < LEAST(date_trunc('day', from_ts) + interval '1 day', to_ts)

UNION ALL

-- Body: daily granularity for full days in between
SELECT bucket,
       datastream_id,
       total_value,
       avg_value,
       min_value,
       max_value,
       sample_count
FROM energy_daily
WHERE bucket >= date_trunc('day', from_ts) + interval '1 day'
  AND bucket < date_trunc('day', to_ts)

UNION ALL

-- Tail: hourly granularity for the last partial day (only if different day)
SELECT bucket,
       datastream_id,
       total_value,
       total_value / NULLIF(sample_count, 0),
       min_value,
       max_value,
       sample_count
FROM energy_hourly
WHERE date_trunc('day', to_ts) > date_trunc('day', from_ts)
  AND bucket >= date_trunc('day', to_ts)
  AND bucket <= to_ts

ORDER BY bucket;
$$
LANGUAGE SQL STABLE;

CREATE TABLE IF NOT EXISTS device_baseline
(
    datastream_id
    TEXT
    NOT
    NULL
    REFERENCES datastream (datastream_id),
    hour_of_day
    INT
    NOT
    NULL,
    minute_bucket
    INT
    NOT
    NULL,
    expected_value
    DOUBLE
    PRECISION
    NOT
    NULL,
    recorded_at
    TIMESTAMPTZ
    DEFAULT
    NOW
(
),
    PRIMARY KEY
(
    datastream_id,
    hour_of_day,
    minute_bucket
)
    );

-- ── Calibration / baseline-collection orchestration ──────────────────────────
-- Replaces the old per-observation baseline_delta_log + delta-batch +
-- baseline-drift-check. The baseline is now rebuilt from genuine full ("raw")
-- collection days (cbl_* below); drift is detected cheaply from an hourly
-- continuous aggregate instead of a per-reading delta table.

-- One row per datastream: the orchestrator's work list + active lease.
CREATE TABLE IF NOT EXISTS calibration_state
(
    datastream_id     TEXT PRIMARY KEY REFERENCES datastream (datastream_id),
    thing_id          TEXT,
    status            TEXT NOT NULL DEFAULT 'idle',   -- 'idle' | 'collecting'
    needs_calibration BOOLEAN NOT NULL DEFAULT FALSE, -- set by flag-needs-calibration
    drift_score       DOUBLE PRECISION DEFAULT 0,    -- magnitude of drift; higher = more urgent
    flagged_at        TIMESTAMPTZ,
    lease_started_at  TIMESTAMPTZ,
    lease_expires_at  TIMESTAMPTZ,
    last_collected_at TIMESTAMPTZ
);

-- Partial index: orchestrator picks top-N by drift score in O(log n)
CREATE INDEX IF NOT EXISTS idx_calibration_priority
    ON calibration_state (drift_score DESC)
    WHERE needs_calibration = TRUE AND status = 'idle';

-- One pre-aggregated value per (datastream, collected full day, hour-of-day).
-- Filled from a full raw collection day; CBL averages the last X of these.
CREATE TABLE IF NOT EXISTS cbl_day_bucket
(
    datastream_id TEXT NOT NULL REFERENCES datastream (datastream_id),
    day           DATE NOT NULL,
    hour_of_day   INT  NOT NULL,
    avg_value     DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (datastream_id, day, hour_of_day)
);

-- pg_cron drift detection (optional — CalibrationOrchestrator handles this too)
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_cron;

    PERFORM cron.schedule('flag-needs-calibration', '0 * * * *', $cron$

        -- 1. ensure every datastream the devices emit has a calibration_state row
        INSERT INTO calibration_state (datastream_id, thing_id)
        SELECT DISTINCT h.datastream_id, ds.thing_id::TEXT
        FROM energy_hourly h
        LEFT JOIN datastream ds ON ds.datastream_id = h.datastream_id
        ON CONFLICT (datastream_id) DO NOTHING;

        -- 2. DRIFT: recent hourly averages sitting far from the baseline
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
          AND d.drift_score      > 0.3      -- DRIFT_THRESHOLD (raw units; tune per sensor)
          AND d.n_hours          > 24       -- MIN_HOURS
          AND cs.status            = 'idle'
          AND cs.needs_calibration = FALSE
          AND (cs.last_collected_at IS NULL
               OR cs.last_collected_at < NOW() - interval '1 day');   -- cooldown

        -- 3. COLD START: datastreams that have no baseline yet (highest priority)
        UPDATE calibration_state cs
        SET needs_calibration = TRUE, drift_score = 9999, flagged_at = NOW()
        WHERE cs.status = 'idle'
          AND cs.needs_calibration = FALSE
          AND NOT EXISTS (SELECT 1 FROM device_baseline b
                          WHERE b.datastream_id = cs.datastream_id);
    $cron$
    );
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'pg_cron not available — skipping scheduled drift detection (CalibrationOrchestrator handles it)';
END;
$$;
-- hour (the "DX / last-X-days" CBL). Called by the orchestrator after a
-- collection day completes.
CREATE OR REPLACE FUNCTION cbl_rebuild_baseline(p_datastream TEXT, p_x_days INT)
    RETURNS void AS $$
    INSERT INTO device_baseline (datastream_id, hour_of_day, minute_bucket, expected_value, recorded_at)
    SELECT p_datastream, hour_of_day, 0, AVG(avg_value), NOW()
    FROM (
        SELECT hour_of_day, avg_value,
               row_number() OVER (PARTITION BY hour_of_day ORDER BY day DESC) AS recent
        FROM cbl_day_bucket
        WHERE datastream_id = p_datastream
    ) r
    WHERE recent <= p_x_days
    GROUP BY hour_of_day
    ON CONFLICT (datastream_id, hour_of_day, minute_bucket)
    DO UPDATE SET expected_value = EXCLUDED.expected_value, recorded_at = NOW();
$$ LANGUAGE SQL;

-- Aggregate one completed collection window's raw observations into one row per
-- hour-of-day in cbl_day_bucket. Called by the orchestrator on lease reclaim.
CREATE OR REPLACE FUNCTION cbl_ingest_day(p_datastream TEXT, p_from TIMESTAMPTZ, p_to TIMESTAMPTZ)
    RETURNS void AS $$
    INSERT INTO cbl_day_bucket (datastream_id, day, hour_of_day, avg_value)
    SELECT p_datastream, p_from::date, EXTRACT(HOUR FROM "timestamp")::INT, AVG(value)
    FROM observation
    WHERE datastream_id = p_datastream
      AND "timestamp" >= p_from AND "timestamp" < p_to
    GROUP BY EXTRACT(HOUR FROM "timestamp")::INT
    ON CONFLICT (datastream_id, day, hour_of_day)
    DO UPDATE SET avg_value = EXCLUDED.avg_value;
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION cbl_reclaim_leases(p_x_days INT DEFAULT 5)
    RETURNS void AS $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT datastream_id, lease_started_at, lease_expires_at
        FROM calibration_state
        WHERE status = 'collecting' AND lease_expires_at <= NOW()
    LOOP
        -- Summarize raw observations into cbl_day_bucket
        PERFORM cbl_ingest_day(r.datastream_id, r.lease_started_at, r.lease_expires_at);

        -- Rebuild baseline from last X collection days
        PERFORM cbl_rebuild_baseline(r.datastream_id, p_x_days);

        -- Release the lease
        UPDATE calibration_state
        SET status = 'idle',
            needs_calibration = FALSE,
            drift_score = 0,
            last_collected_at = NOW()
        WHERE datastream_id = r.datastream_id;
    END LOOP;
END;
$$ LANGUAGE plpgsql;