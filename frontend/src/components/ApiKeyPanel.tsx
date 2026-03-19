import { useState, useEffect, useCallback } from 'react'
import type { CSSProperties } from 'react'
import toast from 'react-hot-toast'
import type { ApiKey, ApiKeyCreatedResponse } from '../types'
import { keyApi } from '../api/keys'

const s = {
  panel: {
    position: 'absolute', right: 0, top: 0, bottom: 0, width: 340,
    background: '#1e293b', borderLeft: '1px solid #334155',
    display: 'flex', flexDirection: 'column', zIndex: 1000,
    overflow: 'hidden',
  } as CSSProperties,
  header: {
    padding: '0.75rem 1rem', borderBottom: '1px solid #334155',
    fontWeight: 700, fontSize: '0.9rem', color: '#f1f5f9',
  } as CSSProperties,
  form: {
    padding: '0.75rem 1rem', borderBottom: '1px solid #334155',
    display: 'flex', flexDirection: 'column', gap: '0.5rem',
  } as CSSProperties,
  input: {
    padding: '0.35rem 0.5rem', borderRadius: 6, border: '1px solid #334155',
    background: '#0f172a', color: '#f1f5f9', fontSize: '0.82rem', outline: 'none',
  } as CSSProperties,
  btn: (variant: 'primary' | 'danger' | 'ghost'): CSSProperties => ({
    padding: '0.35rem 0.75rem', borderRadius: 6, border: '1px solid #334155',
    cursor: 'pointer', fontSize: '0.8rem',
    background: variant === 'primary' ? '#3b82f6' : 'transparent',
    color: variant === 'danger' ? '#ef4444' : variant === 'primary' ? '#fff' : '#94a3b8',
  }),
  list: { flex: 1, overflowY: 'auto', padding: '0.5rem' } as CSSProperties,
  keyRow: {
    padding: '0.6rem', borderRadius: 6, marginBottom: '0.4rem',
    background: '#0f172a', fontSize: '0.78rem',
  } as CSSProperties,
  meta: { color: '#64748b', marginTop: '0.2rem' } as CSSProperties,
  rawKey: {
    fontFamily: 'monospace', fontSize: '0.72rem', wordBreak: 'break-all',
    padding: '0.4rem', borderRadius: 4, background: '#064e3b', color: '#6ee7b7',
    marginTop: '0.4rem',
  } as CSSProperties,
}

export function ApiKeyPanel() {
  const [keys, setKeys] = useState<ApiKey[]>([])
  const [newKeyName, setNewKeyName] = useState('')
  const [newKeyRate, setNewKeyRate] = useState('60')
  const [creating, setCreating] = useState(false)
  const [revealed, setRevealed] = useState<ApiKeyCreatedResponse | null>(null)

  const load = useCallback(async () => {
    try { setKeys(await keyApi.list()) } catch { /* ignore */ }
  }, [])

  useEffect(() => { load() }, [load])

  const handleCreate = async () => {
    const name = newKeyName.trim()
    const rate = parseInt(newKeyRate, 10)
    if (!name || isNaN(rate) || rate < 1) {
      toast.error('Name and valid rate limit required')
      return
    }
    setCreating(true)
    try {
      const created = await keyApi.create(name, rate)
      setRevealed(created)
      setNewKeyName('')
      setNewKeyRate('60')
      await load()
      toast.success(`Key "${name}" created — copy it now, it won't be shown again`)
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Failed to create key')
    } finally {
      setCreating(false)
    }
  }

  const handleRevoke = async (key: ApiKey) => {
    if (!confirm(`Revoke key "${key.name}"? This cannot be undone.`)) return
    try {
      await keyApi.revoke(key.id)
      toast.success(`Revoked "${key.name}"`)
      await load()
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Failed to revoke key')
    }
  }

  return (
    <div style={s.panel}>
      <div style={s.header}>API Keys</div>

      {/* Create form */}
      <div style={s.form}>
        <input
          style={s.input}
          placeholder="Key name"
          value={newKeyName}
          onChange={e => setNewKeyName(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && handleCreate()}
        />
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          <input
            style={{ ...s.input, width: 80 }}
            type="number"
            min={1}
            max={10000}
            placeholder="req/min"
            value={newKeyRate}
            onChange={e => setNewKeyRate(e.target.value)}
          />
          <span style={{ color: '#64748b', fontSize: '0.78rem' }}>req/min</span>
          <button style={{ ...s.btn('primary'), marginLeft: 'auto' }} onClick={handleCreate} disabled={creating}>
            {creating ? 'Creating…' : '+ Create'}
          </button>
        </div>

        {/* Show raw key once after creation */}
        {revealed && (
          <div>
            <div style={s.rawKey}>{revealed.rawKey}</div>
            <div style={{ color: '#64748b', fontSize: '0.72rem', marginTop: '0.25rem' }}>
              Copy this key now — it will not be shown again.
            </div>
            <button style={{ ...s.btn('ghost'), marginTop: '0.4rem', fontSize: '0.75rem' }} onClick={() => setRevealed(null)}>
              Dismiss
            </button>
          </div>
        )}
      </div>

      {/* Key list */}
      <div style={s.list}>
        {keys.length === 0 && (
          <div style={{ color: '#475569', fontSize: '0.8rem', padding: '1rem', textAlign: 'center' }}>
            No API keys yet
          </div>
        )}
        {keys.map(k => (
          <div key={k.id} style={{ ...s.keyRow, opacity: k.active ? 1 : 0.5 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <span style={{ color: '#e2e8f0', fontWeight: 600 }}>{k.name}</span>
              {k.active && (
                <button style={s.btn('danger')} onClick={() => handleRevoke(k)}>
                  Revoke
                </button>
              )}
            </div>
            <div style={s.meta}>
              Prefix: <span style={{ fontFamily: 'monospace', color: '#94a3b8' }}>{k.keyPrefix}…</span>
            </div>
            <div style={s.meta}>{k.rateLimitPerMinute} req/min</div>
            {k.expiresAt && <div style={s.meta}>Expires: {new Date(k.expiresAt).toLocaleDateString()}</div>}
            {!k.active && <div style={{ color: '#ef4444', fontSize: '0.72rem', marginTop: '0.2rem' }}>Revoked</div>}
          </div>
        ))}
      </div>
    </div>
  )
}
