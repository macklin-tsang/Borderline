// ── Auth ──────────────────────────────────────────────────────────────────────

export interface AuthResponse {
  token: string
  userId: string
  email: string
}

// ── Geofences ─────────────────────────────────────────────────────────────────

export interface GeoJsonGeometry {
  type: string
  coordinates: unknown
}

export interface Geofence {
  id: string
  name: string
  geometry: GeoJsonGeometry
  active: boolean
  alertOnEntry: boolean
  alertOnExit: boolean
  createdAt: string
}

export interface GeofenceRequest {
  name: string
  geometry: GeoJsonGeometry
  alertOnEntry: boolean
  alertOnExit: boolean
}

// ── Devices ───────────────────────────────────────────────────────────────────

export interface Device {
  id: string
  name: string
  lastLat: number | null
  lastLon: number | null
  lastSeen: string | null
  createdAt: string
  insideAnyGeofence: boolean
}

export interface LocationPingRequest {
  lat: number
  lon: number
}

// ── API Keys ──────────────────────────────────────────────────────────────────

export interface ApiKey {
  id: string
  name: string
  keyPrefix: string
  scopes: string[]
  rateLimitPerMinute: number
  expiresAt: string | null
  active: boolean
  createdAt: string
}

export interface ApiKeyCreatedResponse extends ApiKey {
  rawKey: string
}

// ── WebSocket alerts ──────────────────────────────────────────────────────────

export interface GeofenceAlert {
  type: 'ENTER' | 'EXIT'
  deviceId: string
  deviceName: string
  geofenceId: string
  geofenceName: string
  timestamp: string
}

export interface SecurityEvent {
  type: 'RATE_LIMIT' | 'INVALID_KEY'
  apiKeyId: string | null
  endpoint: string
  detail: string
  timestamp: string
}

// ── Security dashboard ────────────────────────────────────────────────────────

export interface RequestLog {
  id: number
  apiKeyId: string | null
  endpoint: string
  method: string
  statusCode: number
  durationMs: number | null
  wasBlocked: boolean
  blockReason: string | null
  createdAt: string
}
