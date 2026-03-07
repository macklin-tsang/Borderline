import { useState, useCallback } from 'react'
import type { SecurityEvent, RequestLog } from '../types'
import { api } from '../api/client'

interface Props {
  liveEvents: SecurityEvent[]
}

const typeColor = (t: string) =>
  t === 'GEO_VIOLATION' ? '#ef4444' : t === 'RATE_LIMIT' ? '#f59e0b' : '#6366f1'

const s = {
  panel: {
    position: 'absolute', right: 0, top: 0, bottom: 0, width: 340,
    background: '#1e293b', borderLeft: '1px solid #334155',
    display: 'flex', flexDirection: 'column', zIndex: 1000,
    overflow: 'hidden',
  },
  header: {
    padding: '0.75rem 1rem', borderBottom: '1px solid #334155',
    display: 'flex', alignItems: 'center', justifyContent: 'space-between',
  },
  title: { fontWeight: 700, fontSize: '0.9rem', color: '#f1f5f9' },
  tabRow: { display: 'flex', borderBottom: '1px solid #334155' },
  tab: (active: boolean): React.CSSProperties => ({
    flex: 1, padding: '0.5rem', fontSize: '0.8rem', cursor: 'pointer',
    border: 'none', background: 'transparent',
    color: active ? '#3b82f6' : '#64748b',
    borderBottom: active ? '2px solid #3b82f6' : '2px solid transparent',
  }),
  list: { flex: 1, overflowY: 'auto', padding: '0.5rem' },
  eventRow: {
    padding: '0.5rem', borderRadius: 6, marginBottom: '0.4rem',
    background: '#0f172a', fontSize: '0.78rem',
  },
  badge: (color: string): React.CSSProperties => ({
    display: 'inline-block', padding: '0.1rem 0.4rem', borderRadius: 4,
    background: color + '22', color, fontSize: '0.7rem', fontWeight: 700,
    marginBottom: '0.25rem',
  }),
  meta: { color: '#64748b', marginTop: '0.15rem' },
  loadBtn: {
    margin: '0.5rem', padding: '0.4rem', borderRadius: 6, border: '1px solid #334155',
    background: 'transparent', color: '#94a3b8', cursor: 'pointer', fontSize: '0.8rem',
  },
}

export function SecurityPanel({ liveEvents }: Props) {
  const [tab, setTab] = useState<'live' | 'history'>('live')
  const [history, setHistory] = useState<RequestLog[]>([])
  const [loading, setLoading] = useState(false)

  const loadHistory = useCallback(async () => {
    setLoading(true)
    try {
      const logs = await api.get<RequestLog[]>('/security/events?limit=100')
      setHistory(logs)
    } catch { /* ignore */ } finally {
      setLoading(false)
    }
  }, [])

  return (
    <div style={s.panel}>
      <div style={s.header}>
        <span style={s.title}>Security Dashboard</span>
        <span style={{ fontSize: '0.75rem', color: '#64748b' }}>
          {liveEvents.length} live
        </span>
      </div>
      <div style={s.tabRow}>
        <button style={s.tab(tab === 'live')} onClick={() => setTab('live')}>Live Events</button>
        <button style={s.tab(tab === 'history')} onClick={() => { setTab('history'); loadHistory() }}>
          History
        </button>
      </div>

      {tab === 'live' && (
        <div style={s.list}>
          {liveEvents.length === 0 && (
            <div style={{ color: '#475569', fontSize: '0.8rem', padding: '1rem', textAlign: 'center' }}>
              No security events — all clear
            </div>
          )}
          {[...liveEvents].reverse().map((ev, i) => (
            <div key={i} style={s.eventRow}>
              <div style={s.badge(typeColor(ev.type))}>{ev.type}</div>
              <div style={{ color: '#e2e8f0' }}>{ev.endpoint}</div>
              <div style={s.meta}>{ev.detail}</div>
              {ev.countryCode && <div style={s.meta}>Country: {ev.countryCode}</div>}
              <div style={s.meta}>{new Date(ev.timestamp).toLocaleTimeString()}</div>
            </div>
          ))}
        </div>
      )}

      {tab === 'history' && (
        <>
          <button style={s.loadBtn} onClick={loadHistory} disabled={loading}>
            {loading ? 'Loading…' : '↺ Refresh'}
          </button>
          <div style={s.list}>
            {history.map(log => (
              <div key={log.id} style={s.eventRow}>
                {log.wasBlocked && (
                  <div style={s.badge('#ef4444')}>{log.blockReason ?? 'BLOCKED'}</div>
                )}
                <div style={{ color: '#e2e8f0' }}>
                  <span style={{ color: '#94a3b8' }}>{log.method}</span> {log.endpoint}
                </div>
                <div style={s.meta}>
                  {log.statusCode}
                  {log.countryCode && ` · ${log.countryCode}`}
                  {log.durationMs != null && ` · ${log.durationMs}ms`}
                </div>
                <div style={s.meta}>{new Date(log.createdAt).toLocaleString()}</div>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  )
}
