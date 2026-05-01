CREATE TABLE IF NOT EXISTS "user" (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    oauth_provider VARCHAR(50),
    oauth_provider_id VARCHAR(255),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS thing (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES "user"(uuid),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    longitude DOUBLE PRECISION,
    latitude DOUBLE PRECISION,
    status VARCHAR(50) DEFAULT 'active',
    last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS sensor (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    metadata JSONB
);

CREATE TABLE IF NOT EXISTS observed_property (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    definition TEXT,
    unit_of_measurement VARCHAR(50),
    symbol VARCHAR(10)
);

CREATE TABLE IF NOT EXISTS datastream (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    thing_id UUID REFERENCES thing(uuid),
    sensor_id UUID REFERENCES sensor(uuid),
    observed_property_id UUID REFERENCES observed_property(uuid),
    name VARCHAR(255) NOT NULL,
    observation_type VARCHAR(50),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS observation (
    "timestamp" TIMESTAMPTZ NOT NULL,
    datastream_id TEXT NOT NULL,
    value DOUBLE PRECISION NOT NULL
);

SELECT create_hypertable('observation', 'timestamp', if_not_exists => TRUE);

CREATE INDEX IF NOT EXISTS idx_observation_datastream
    ON observation (datastream_id, "timestamp" DESC);
