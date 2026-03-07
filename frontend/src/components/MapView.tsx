import { useEffect, useRef, useState, useCallback } from 'react'
import L from 'leaflet'
import 'leaflet-draw'
import toast from 'react-hot-toast'
import type { Geofence, Device, GeofenceAlert, SecurityEvent } from '../types'
import { geofenceApi } from '../api/geofences'
import { deviceApi } from '../api/devices'
import { useWebSocket } from '../hooks/useWebSocket'
import { SecurityPanel } from './SecurityPanel'

interface Props {
  token: string
  onLogout: () => void
}

// Fix Leaflet default icon paths broken by bundlers
delete (L.Icon.Default.prototype as unknown as Record<string, unknown>)._getIconUrl
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
})

export function MapView({ token, onLogout }: Props) {
  const mapRef = useRef<L.Map | null>(null)
  const mapDivRef = useRef<HTMLDivElement>(null)
  const geofenceLayerRef = useRef<L.FeatureGroup>(new L.FeatureGroup())
  const deviceLayerRef = useRef<L.LayerGroup>(new L.LayerGroup())
  const deviceMarkersRef = useRef<Map<string, L.CircleMarker>>(new Map())

  const [geofences, setGeofences] = useState<Geofence[]>([])
  const [devices, setDevices] = useState<Device[]>([])
  const [securityEvents, setSecurityEvents] = useState<SecurityEvent[]>([])
  const [showSecurity, setShowSecurity] = useState(false)

  // Load geofences + devices
  const refresh = useCallback(async () => {
    const [gfs, devs] = await Promise.all([geofenceApi.list(), deviceApi.list()])
    setGeofences(gfs)
    setDevices(devs)
  }, [])

  // WebSocket alert handler
  const handleAlert = useCallback((alert: GeofenceAlert) => {
    const isEntry = alert.type === 'ENTER'
    toast(
      `${isEntry ? '↘ ENTER' : '↗ EXIT'} — ${alert.deviceName} ${isEntry ? 'entered' : 'left'} ${alert.geofenceName}`,
      { style: { background: isEntry ? '#16a34a' : '#d97706', color: '#fff', fontWeight: 600 } }
    )
    refresh()
  }, [refresh])

  const handleSecurityEvents = useCallback((events: SecurityEvent[]) => {
    setSecurityEvents(prev => [...prev, ...events].slice(-200))
    events.forEach(ev => {
      if (ev.type === 'GEO_VIOLATION') {
        toast.error(`Geo-violation blocked: ${ev.endpoint} (${ev.countryCode})`)
      }
    })
  }, [])

  useWebSocket({ token, onAlert: handleAlert, onSecurityEvents: handleSecurityEvents })

  // Init map
  useEffect(() => {
    if (!mapDivRef.current || mapRef.current) return
    const map = L.map(mapDivRef.current).setView([37.7749, -122.4194], 11)
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© <a href="https://openstreetmap.org">OpenStreetMap</a>',
    }).addTo(map)

    geofenceLayerRef.current.addTo(map)
    deviceLayerRef.current.addTo(map)

    // leaflet-draw toolbar — polygon + rectangle
    const drawControl = new L.Control.Draw({
      draw: {
        polygon: { allowIntersection: false, showArea: true },
        rectangle: {},
        circle: false, circlemarker: false, marker: false, polyline: false,
      },
      edit: { featureGroup: geofenceLayerRef.current },
    })
    map.addControl(drawControl)

    // On draw:created → prompt for name → POST to API
    map.on(L.Draw.Event.CREATED, async (e: L.LeafletEvent) => {
      const layer = (e as L.DrawEvents.Created).layer
      const geoJson = layer.toGeoJSON()
      const name = prompt('Geofence name:')
      if (!name?.trim()) return
      try {
        await geofenceApi.create({
          name: name.trim(),
          geometry: geoJson.geometry as Geofence['geometry'],
          alertOnEntry: true,
          alertOnExit: true,
        })
        toast.success(`Geofence "${name}" created`)
        refresh()
      } catch (err) {
        toast.error(err instanceof Error ? err.message : 'Failed to create geofence')
      }
    })

    mapRef.current = map
    return () => { map.remove(); mapRef.current = null }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  // Render geofence polygons whenever the list changes
  useEffect(() => {
    const layer = geofenceLayerRef.current
    layer.clearLayers()
    geofences.forEach(gf => {
      try {
        const geoJsonLayer = L.geoJSON(gf.geometry as GeoJSON.Geometry, {
          style: { color: '#3b82f6', weight: 2, fillOpacity: 0.15 },
        })
        geoJsonLayer.bindTooltip(gf.name, { permanent: false, direction: 'center' })
        geoJsonLayer.on('click', () => {
          if (confirm(`Delete geofence "${gf.name}"?`)) {
            geofenceApi.delete(gf.id).then(() => {
              toast.success(`Deleted "${gf.name}"`)
              refresh()
            })
          }
        })
        layer.addLayer(geoJsonLayer)
      } catch { /* skip malformed geometry */ }
    })
  }, [geofences]) // eslint-disable-line react-hooks/exhaustive-deps

  // Render device markers whenever device list changes
  useEffect(() => {
    const markerMap = deviceMarkersRef.current
    const layer = deviceLayerRef.current

    // Update or add markers
    devices.forEach(dev => {
      if (dev.lastLat == null || dev.lastLon == null) return
      const inside = false // optimistic — real state comes from backend
      const existing = markerMap.get(dev.id)
      const latlng = L.latLng(dev.lastLat, dev.lastLon)

      if (existing) {
        existing.setLatLng(latlng)
      } else {
        const marker = L.circleMarker(latlng, {
          radius: 8,
          color: inside ? '#22c55e' : '#94a3b8',
          fillColor: inside ? '#22c55e' : '#94a3b8',
          fillOpacity: 0.8,
          weight: 2,
        })
        marker.bindTooltip(dev.name, { permanent: false })
        marker.addTo(layer)
        markerMap.set(dev.id, marker)
      }
    })

    // Remove markers for devices no longer in list
    const deviceIds = new Set(devices.map(d => d.id))
    markerMap.forEach((marker, id) => {
      if (!deviceIds.has(id)) {
        marker.remove()
        markerMap.delete(id)
      }
    })
  }, [devices])

  // Initial load + polling every 5s
  useEffect(() => {
    refresh()
    const interval = setInterval(refresh, 5000)
    return () => clearInterval(interval)
  }, [refresh])

  return (
    <div style={{ position: 'relative', height: '100%', display: 'flex', flexDirection: 'column' }}>
      {/* Top bar */}
      <div style={{
        padding: '0.5rem 1rem', background: '#1e293b', borderBottom: '1px solid #334155',
        display: 'flex', alignItems: 'center', gap: '1rem', zIndex: 1001,
      }}>
        <span style={{ fontWeight: 700, fontSize: '1rem', color: '#f1f5f9' }}>Borderline</span>
        <span style={{ color: '#64748b', fontSize: '0.85rem' }}>
          {geofences.length} geofences · {devices.length} devices
        </span>
        <div style={{ flex: 1 }} />
        <button
          onClick={() => setShowSecurity(s => !s)}
          style={{
            padding: '0.35rem 0.75rem', borderRadius: 6, border: '1px solid #334155',
            background: showSecurity ? '#3b82f6' : 'transparent',
            color: showSecurity ? '#fff' : '#94a3b8', cursor: 'pointer', fontSize: '0.85rem',
          }}
        >
          Security
          {securityEvents.length > 0 && (
            <span style={{
              marginLeft: 6, background: '#ef4444', color: '#fff',
              borderRadius: '50%', padding: '0 5px', fontSize: '0.7rem',
            }}>
              {securityEvents.length}
            </span>
          )}
        </button>
        <button
          onClick={onLogout}
          style={{
            padding: '0.35rem 0.75rem', borderRadius: 6, border: '1px solid #334155',
            background: 'transparent', color: '#94a3b8', cursor: 'pointer', fontSize: '0.85rem',
          }}
        >
          Sign out
        </button>
      </div>

      {/* Map + optional security panel */}
      <div style={{ flex: 1, position: 'relative' }}>
        <div
          ref={mapDivRef}
          style={{ position: 'absolute', inset: 0, right: showSecurity ? 340 : 0 }}
        />
        {showSecurity && <SecurityPanel liveEvents={securityEvents} />}
      </div>
    </div>
  )
}
