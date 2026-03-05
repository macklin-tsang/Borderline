-- Enable PostGIS (idempotent — safe to run on already-enabled DB)
CREATE EXTENSION IF NOT EXISTS postgis;

-- ─────────────────────────────────────────────
-- Users
-- ─────────────────────────────────────────────
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ─────────────────────────────────────────────
-- Geofences
-- geometry column uses SRID 4326 (WGS 84 — standard GPS coordinates)
-- ─────────────────────────────────────────────
CREATE TABLE geofences (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name           VARCHAR(255) NOT NULL,
    geometry       GEOMETRY(GEOMETRY, 4326) NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    alert_on_entry BOOLEAN      NOT NULL DEFAULT TRUE,
    alert_on_exit  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ─────────────────────────────────────────────
-- Devices
-- last_lat/last_lon stored separately for direct queries;
-- PostGIS point derived on demand via ST_Point(last_lon, last_lat)
-- ─────────────────────────────────────────────
CREATE TABLE devices (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(255) NOT NULL,
    last_lat   DOUBLE PRECISION,
    last_lon   DOUBLE PRECISION,
    last_seen  TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ─────────────────────────────────────────────
-- Device ↔ Geofence crossing state
-- PK doubles as the unique constraint — no separate UNIQUE index needed.
-- Use INSERT ... ON CONFLICT DO UPDATE (upsert) in application code.
-- ─────────────────────────────────────────────
CREATE TABLE device_geofence_state (
    device_id   UUID    NOT NULL REFERENCES devices(id)   ON DELETE CASCADE,
    geofence_id UUID    NOT NULL REFERENCES geofences(id) ON DELETE CASCADE,
    inside      BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (device_id, geofence_id)
);

-- ─────────────────────────────────────────────
-- API Keys
-- key_prefix: first 8 chars of the full key, stored plaintext for O(1) lookup.
-- key_hash:   HMAC-SHA256(fullKey, serverSecret) — deterministic, verifiable.
-- scopes:     PostgreSQL text array — avoids a join table for a bounded value set.
-- ─────────────────────────────────────────────
CREATE TABLE api_keys (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                VARCHAR(255) NOT NULL,
    key_prefix          VARCHAR(8)   NOT NULL,
    key_hash            VARCHAR(64)  NOT NULL,
    hmac_key_version    INTEGER      NOT NULL DEFAULT 1,
    scopes              TEXT[]       NOT NULL DEFAULT '{}',
    rate_limit_per_minute INTEGER    NOT NULL DEFAULT 100,
    expires_at          TIMESTAMPTZ,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ─────────────────────────────────────────────
-- API Key geo-restrictions
-- Restricts which geofences a key's caller must be inside.
-- No restrictions = unrestricted (table has no rows for that key).
-- ─────────────────────────────────────────────
CREATE TABLE api_key_geo_restrictions (
    api_key_id  UUID NOT NULL REFERENCES api_keys(id)   ON DELETE CASCADE,
    geofence_id UUID NOT NULL REFERENCES geofences(id)  ON DELETE CASCADE,
    PRIMARY KEY (api_key_id, geofence_id)
);

-- ─────────────────────────────────────────────
-- API Request audit log
-- BIGSERIAL PK — high insert throughput, no UUID generation overhead.
-- Retention: delete rows older than 30 days (scheduled job, implemented in RequestLogService).
--   DELETE FROM api_request_logs WHERE created_at < NOW() - INTERVAL '30 days';
-- ─────────────────────────────────────────────
CREATE TABLE api_request_logs (
    id           BIGSERIAL    PRIMARY KEY,
    api_key_id   UUID         REFERENCES api_keys(id) ON DELETE SET NULL,
    endpoint     VARCHAR(255) NOT NULL,
    method       VARCHAR(10)  NOT NULL,
    status_code  INTEGER      NOT NULL,
    country_code VARCHAR(2),
    duration_ms  INTEGER,
    was_blocked  BOOLEAN      NOT NULL DEFAULT FALSE,
    block_reason VARCHAR(50),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
