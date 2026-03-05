-- ─────────────────────────────────────────────
-- Spatial index on geofences.geometry
-- GiST (Generalized Search Tree) supports R-tree structure for 2D spatial data.
-- Required for the && bounding-box pre-filter in ST_Contains queries.
-- Without this, every location update scans all geofences in the table.
-- ─────────────────────────────────────────────
CREATE INDEX idx_geofences_geometry ON geofences USING GIST (geometry);

-- ─────────────────────────────────────────────
-- Composite B-tree for active geofence lookup per user
-- Query pattern: WHERE user_id = ? AND active = true
-- Composite covers both conditions — more selective than two separate indexes.
-- ─────────────────────────────────────────────
CREATE INDEX idx_geofences_user_active ON geofences (user_id, active);

-- ─────────────────────────────────────────────
-- API key prefix lookup (O(1) in filter chain)
-- Filter chain extracts prefix from X-API-Key header → single row lookup.
-- ─────────────────────────────────────────────
CREATE INDEX idx_api_keys_prefix ON api_keys (key_prefix);

-- ─────────────────────────────────────────────
-- Request log indexes
-- created_at: used by the TTL cleanup job (range delete by date)
-- api_key_id: used by the security dashboard to fetch events per key
-- ─────────────────────────────────────────────
CREATE INDEX idx_api_request_logs_created_at ON api_request_logs (created_at);
CREATE INDEX idx_api_request_logs_api_key_id ON api_request_logs (api_key_id);

-- ─────────────────────────────────────────────
-- Devices: user lookup
-- ─────────────────────────────────────────────
CREATE INDEX idx_devices_user_id ON devices (user_id);
