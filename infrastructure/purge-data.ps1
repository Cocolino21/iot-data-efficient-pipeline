<#
    Purge ingested/runtime rows from the IoT TimescaleDB.

    Keeps configuration/reference data (user, thing, sensor,
    observed_property, datastream); truncates observation, the energy_*
    continuous aggregates, and the calibration/baseline tables.

    Usage (from repo root or anywhere):
        ./infrastructure/purge-data.ps1
        ./infrastructure/purge-data.ps1 -KeepCalibration   # leave calibration/baseline tables alone
#>
param(
    [switch]$KeepCalibration
)

$ErrorActionPreference = 'Stop'
$compose = Join-Path $PSScriptRoot 'docker-compose.yml'

$sql = @'
BEGIN;
TRUNCATE TABLE observation;
TRUNCATE TABLE energy_hourly;
TRUNCATE TABLE energy_daily;
TRUNCATE TABLE energy_weekly;
TRUNCATE TABLE energy_monthly;
'@

if (-not $KeepCalibration) {
    $sql += @'

TRUNCATE TABLE cbl_day_bucket;
TRUNCATE TABLE device_baseline;
TRUNCATE TABLE calibration_state;
'@
}

$sql += @'

COMMIT;
SELECT 'observation' AS table, count(*) FROM observation
UNION ALL SELECT 'energy_hourly',  count(*) FROM energy_hourly
UNION ALL SELECT 'energy_daily',   count(*) FROM energy_daily
UNION ALL SELECT 'energy_weekly',  count(*) FROM energy_weekly
UNION ALL SELECT 'energy_monthly', count(*) FROM energy_monthly;
'@

Write-Host 'Purging ingested data from TimescaleDB (iot)...' -ForegroundColor Cyan
$sql | docker compose -f $compose exec -T timescaledb psql -U postgres -d iot -v ON_ERROR_STOP=1 -f -
Write-Host 'Done.' -ForegroundColor Green
