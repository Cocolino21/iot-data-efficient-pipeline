-- Purge ingested/runtime data from the IoT database.
--
-- Removes everything that gets populated by the running pipeline while
-- KEEPING the configuration/reference rows (user, thing, sensor,
-- observed_property, datastream). Run against the "iot" database.
--
--   docker compose -f infrastructure/docker-compose.yml exec -T timescaledb \
--       psql -U postgres -d iot -f - < infrastructure/purge-data.sql
--
-- or use the wrapper: infrastructure/purge-data.ps1

BEGIN;

-- 1. Raw telemetry written by sink-service (the hypertable).
TRUNCATE TABLE observation;

-- 2. Continuous aggregates derived from observation. They do not empty
--    automatically when the source hypertable is truncated, so clear their
--    materialized data too (child rolls up from parent, so order matters).
TRUNCATE TABLE energy_hourly;
TRUNCATE TABLE energy_daily;
TRUNCATE TABLE energy_weekly;
TRUNCATE TABLE energy_monthly;

-- 3. Calibration / baseline pipeline state populated during ingestion.
TRUNCATE TABLE cbl_day_bucket;
TRUNCATE TABLE device_baseline;
TRUNCATE TABLE calibration_state;

COMMIT;

-- Show row counts after the purge so you can confirm it worked.
SELECT 'observation'      AS table, count(*) FROM observation
UNION ALL SELECT 'energy_hourly',      count(*) FROM energy_hourly
UNION ALL SELECT 'energy_daily',       count(*) FROM energy_daily
UNION ALL SELECT 'energy_weekly',      count(*) FROM energy_weekly
UNION ALL SELECT 'energy_monthly',     count(*) FROM energy_monthly
UNION ALL SELECT 'cbl_day_bucket',     count(*) FROM cbl_day_bucket
UNION ALL SELECT 'device_baseline',    count(*) FROM device_baseline
UNION ALL SELECT 'calibration_state',  count(*) FROM calibration_state;
