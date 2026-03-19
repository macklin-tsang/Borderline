import { useEffect, useRef } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import type { GeofenceAlert, SecurityEvent } from '../types'

interface UseWebSocketOptions {
  token: string | null
  onAlert?: (alert: GeofenceAlert) => void
  onSecurityEvents?: (events: SecurityEvent[]) => void
}

/**
 * Connects to the STOMP WebSocket and subscribes to:
 *   /user/queue/alerts            — geofence crossing alerts
 *   /user/queue/security-events   — batched security dashboard events
 *
 * JWT is passed in the STOMP CONNECT "login" header (not URL query param —
 * query params appear in access logs and browser history).
 */
export function useWebSocket({ token, onAlert, onSecurityEvents }: UseWebSocketOptions) {
  const clientRef = useRef<Client | null>(null)

  // Callback refs — always current without triggering reconnect on every render.
  // Closures inside onConnect capture these refs, not the props directly.
  const onAlertRef = useRef(onAlert)
  const onSecurityEventsRef = useRef(onSecurityEvents)
  onAlertRef.current = onAlert
  onSecurityEventsRef.current = onSecurityEvents

  useEffect(() => {
    if (!token) return

    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      connectHeaders: {
        login: token,   // JWT in STOMP CONNECT frame header
      },
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe('/user/queue/alerts', (msg) => {
          try {
            const alert = JSON.parse(msg.body) as GeofenceAlert
            onAlertRef.current?.(alert)
          } catch { /* ignore malformed frames */ }
        })

        client.subscribe('/user/queue/security-events', (msg) => {
          try {
            const events = JSON.parse(msg.body) as SecurityEvent[]
            onSecurityEventsRef.current?.(events)
          } catch { /* ignore malformed frames */ }
        })
      },
    })

    client.activate()
    clientRef.current = client

    return () => {
      client.deactivate()
      clientRef.current = null
    }
  }, [token])

  return clientRef
}
